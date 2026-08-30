uniffi::include_scaffolding!("sonark");

mod cue;
mod models;
mod db;

use std::sync::Arc;
use tokio::sync::Mutex as AsyncMutex;
use std::sync::Mutex as StdMutex;
use reqwest::{Client};
use futures_util::{StreamExt, stream::FuturesUnordered};
use std::collections::HashMap;
use crate::models::*;
use crate::db::Database;
use lofty::prelude::*;
use lofty::file::{TaggedFileExt, AudioFile};
use std::path::Path;
use serde::Deserialize;

pub fn get_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

pub trait SonarkObserver: Send + Sync {
    fn on_download_progress(&self, progress: DownloadProgress);
    fn on_sync_complete(&self, songs: Vec<Song>);
    fn on_error(&self, message: String);
}

pub trait AuthProvider: Send + Sync {
    fn get_access_token(&self) -> String;
}

pub struct DownloadManager {
    client: Client,
    active_jobs: Arc<AsyncMutex<HashMap<String, tokio::task::JoinHandle<()>>>>,
}

impl DownloadManager {
    pub fn new() -> Self {
        Self {
            client: Client::builder()
                .use_rustls_tls()
                .tcp_keepalive(Some(std::time::Duration::from_secs(60)))
                .build()
                .unwrap(),
            active_jobs: Arc::new(AsyncMutex::new(HashMap::new())),
        }
    }
}

pub struct SonarkEngine {
    download_manager: Arc<DownloadManager>,
    db: Arc<Database>,
    observer: Arc<StdMutex<Option<Box<dyn SonarkObserver>>>>,
    auth_provider: Arc<StdMutex<Option<Box<dyn AuthProvider>>>>,
    runtime: tokio::runtime::Runtime,
}

impl SonarkEngine {
    pub fn new(db_path: String) -> Self {
        #[cfg(target_os = "android")]
        android_logger::init_once(
            android_logger::Config::default()
                .with_tag("SonarkSDK")
                .with_max_level(log::LevelFilter::Debug),
        );

        let rt = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime");

        let db = Database::open(&db_path).expect("Failed to open database");

        Self {
            download_manager: Arc::new(DownloadManager::new()),
            db: Arc::new(db),
            observer: Arc::new(StdMutex::new(None)),
            auth_provider: Arc::new(StdMutex::new(None)),
            runtime: rt,
        }
    }

    pub fn set_observer(&self, observer: Box<dyn SonarkObserver>) {
        *self.observer.lock().unwrap() = Some(observer);
    }

    pub fn set_auth_provider(&self, auth_provider: Box<dyn AuthProvider>) {
        *self.auth_provider.lock().unwrap() = Some(auth_provider);
    }

    pub fn get_all_songs(&self) -> Vec<Song> {
        self.db.get_all_songs().unwrap_or_default()
    }

    pub fn search(&self, query: String) -> Vec<Song> {
        self.db.search_songs(&query).unwrap_or_default()
    }

    pub fn sync_library(&self) {
        let observer = self.observer.clone();
        let client = self.download_manager.client.clone();
        let auth = self.auth_provider.clone();
        let db = self.db.clone();

        self.runtime.spawn(async move {
            let token = {
                let lock = auth.lock().unwrap();
                lock.as_ref().map(|a| a.get_access_token()).unwrap_or_default()
            };

            if token.is_empty() { return; }

            let result = async {
                let vault_id = find_folder(&client, &token, "Vault", "root").await?
                    .ok_or_else(|| anyhow::anyhow!("Vault not found"))?;

                let album_folders = list_all_files(&client, &token, &vault_id, Some("mimeType = 'application/vnd.google-apps.folder'")).await?;

                let mut folder_tasks = FuturesUnordered::new();
                for folder in album_folders {
                    let c = client.clone();
                    let t = token.clone();
                    folder_tasks.push(async move { process_album(&c, &t, folder).await });
                }

                let mut all_songs = Vec::new();
                while let Some(res) = folder_tasks.next().await {
                    if let Ok(mut songs) = res {
                        all_songs.append(&mut songs);
                    }
                }

                all_songs.sort_by(|a, b| a.album.cmp(&b.album).then(a.disc_number.cmp(&b.disc_number)).then(a.track_number.cmp(&b.track_number)));

                db.save_songs(&all_songs)?;
                Ok::<Vec<Song>, anyhow::Error>(all_songs)
            }.await;

            let obs = observer.lock().unwrap();
            if let Some(ref o) = *obs {
                match result {
                    Ok(songs) => o.on_sync_complete(songs),
                    Err(e) => {
                        log::error!("Sync error: {}", e);
                        o.on_error(e.to_string());
                    }
                }
            }
        });
    }

    pub fn start_download(&self, song_id: String, url: String, destination: String) {
        let observer = self.observer.clone();
        let client = self.download_manager.client.clone();
        let jobs = self.download_manager.active_jobs.clone();
        let auth = self.auth_provider.clone();

        self.runtime.spawn(async move {
            let token = {
                let lock = auth.lock().unwrap();
                lock.as_ref().map(|a| a.get_access_token()).unwrap_or_default()
            };

            let mut jobs_lock = jobs.lock().await;
            if jobs_lock.contains_key(&song_id) { return; }

            let sid = song_id.clone();
            let handle = tokio::spawn(async move {
                let _ = async {
                    let response = client.get(&url).bearer_auth(&token).send().await?;
                    let total_size = response.content_length().unwrap_or(0);
                    let mut downloaded: u64 = 0;
                    let mut stream = response.bytes_stream();

                    if let Some(p) = Path::new(&destination).parent() {
                        tokio::fs::create_dir_all(p).await?;
                    }

                    use tokio::io::AsyncWriteExt;
                    let mut file = tokio::fs::File::create(&destination).await?;

                    while let Some(item) = stream.next().await {
                        let chunk = item?;
                        file.write_all(&chunk).await?;
                        downloaded += chunk.len() as u64;

                        let obs = observer.lock().unwrap();
                        if let Some(ref o) = *obs {
                            o.on_download_progress(DownloadProgress {
                                song_id: sid.clone(),
                                downloaded_bytes: downloaded,
                                total_bytes: total_size,
                                status: DownloadStatus::Downloading,
                            });
                        }
                    }
                    file.flush().await?;
                    Ok::<(), anyhow::Error>(())
                }.await;
            });
            jobs_lock.insert(song_id, handle);
        });
    }

    pub fn scan_local_metadata(&self, song_id: String, path: String) -> Option<Song> {
        let p = Path::new(&path);
        if !p.exists() { return None; }

        let tagged_file = lofty::read_from_path(p).ok()?;
        let tag = tagged_file.primary_tag().or_else(|| tagged_file.first_tag())?;

        let properties = tagged_file.properties();

        let songs = self.db.get_all_songs().unwrap_or_default();
        if let Some(mut song) = songs.into_iter().find(|s| s.id == song_id) {
            song.title = tag.title().map(|t| t.to_string()).unwrap_or(song.title);
            song.artist = tag.artist().map(|t| t.to_string()).unwrap_or(song.artist);
            song.album = tag.album().map(|t| t.to_string()).unwrap_or(song.album);
            song.duration_ms = properties.duration().as_millis() as u64;
            song.local_path = Some(path);
            song.download_status = DownloadStatus::Completed;

            let _ = self.db.save_songs(&[song.clone()]);
            return Some(song);
        }
        None
    }
}

// Google Drive API Helpers
#[derive(Deserialize, Debug, Clone)]
struct DriveFile {
    id: String,
    name: String,
    size: Option<String>,
    #[serde(rename = "md5Checksum")]
    md5_checksum: Option<String>,
}

#[derive(Deserialize, Debug)]
struct DriveFileList {
    files: Vec<DriveFile>,
    #[serde(rename = "nextPageToken")]
    next_page_token: Option<String>,
}

async fn fetch_with_retry(client: &Client, token: &str, url: &str) -> anyhow::Result<reqwest::Response> {
    use reqwest::StatusCode;
    let mut retry_count = 0;
    loop {
        let res = client.get(url).bearer_auth(token).send().await;
        match res {
            Ok(r) if r.status() == StatusCode::TOO_MANY_REQUESTS || r.status().is_server_error() => {
                if retry_count < 3 {
                    retry_count += 1;
                    tokio::time::sleep(std::time::Duration::from_secs(2u64.pow(retry_count))).await;
                    continue;
                }
                anyhow::bail!("API error");
            }
            Ok(r) if r.status().is_success() => return Ok(r),
            _ => anyhow::bail!("API failed"),
        }
    }
}

async fn find_folder(client: &Client, token: &str, name: &str, parent_id: &str) -> anyhow::Result<Option<String>> {
    let q = format!("name = '{}' and mimeType = 'application/vnd.google-apps.folder' and '{}' in parents and trashed = false", name, parent_id);
    let url = format!("https://www.googleapis.com/drive/v3/files?q={}&fields=files(id,name)", urlencoding::encode(&q));
    let res: DriveFileList = fetch_with_retry(client, token, &url).await?.json().await?;
    Ok(res.files.first().map(|f| f.id.clone()))
}

async fn list_all_files(client: &Client, token: &str, parent_id: &str, extra_q: Option<&str>) -> anyhow::Result<Vec<DriveFile>> {
    let mut all_files = Vec::new();
    let mut page_token: Option<String> = None;
    loop {
        let mut q = format!("'{}' in parents and trashed = false", parent_id);
        if let Some(extra) = extra_q { q.push_str(" and "); q.push_str(extra); }
        let mut url = format!("https://www.googleapis.com/drive/v3/files?q={}&fields=nextPageToken,files(id,name,size,md5Checksum)&pageSize=1000", urlencoding::encode(&q));
        if let Some(t) = &page_token { url.push_str("&pageToken="); url.push_str(t); }
        let res: DriveFileList = fetch_with_retry(client, token, &url).await?.json().await?;
        all_files.extend(res.files);
        page_token = res.next_page_token;
        if page_token.is_none() { break; }
    }
    Ok(all_files)
}

async fn process_album(client: &Client, token: &str, folder: DriveFile) -> anyhow::Result<Vec<Song>> {
    let (folder_artist, folder_album) = parse_folder_name(&folder.name);
    let files = list_all_files(client, token, &folder.id, None).await?;
    let cover_file = files.iter().find(|f| { let n = f.name.to_lowercase(); n.starts_with("cover.") || n.starts_with("folder.") });
    let cover_url = cover_file.map(|f| format!("https://www.googleapis.com/drive/v3/files/{}?alt=media", f.id));
    let mut songs = Vec::new();
    let cue_files: Vec<&DriveFile> = files.iter().filter(|f| f.name.to_lowercase().ends_with(".cue")).collect();

    if !cue_files.is_empty() {
        for cue_file in cue_files {
            let url = format!("https://www.googleapis.com/drive/v3/files/{}?alt=media", cue_file.id);
            if let Ok(response) = client.get(url).bearer_auth(token).send().await {
                if let Ok(content) = response.text().await {
                    if let Some(sheet) = cue::parse_cue(&content) {
                        if let Some(audio_file) = files.iter().find(|f| f.name.eq_ignore_ascii_case(&sheet.file_name)) {
                            for track in sheet.tracks {
                                songs.push(Song {
                                    id: format!("{}_{}", cue_file.id, track.track_number),
                                    title: track.title,
                                    artist: if track.artist.is_empty() { folder_artist.clone() } else { track.artist },
                                    album: sheet.album.clone(),
                                    duration_ms: 0,
                                    size: audio_file.size.as_deref().unwrap_or("0").parse().unwrap_or(0),
                                    data_url: format!("https://www.googleapis.com/drive/v3/files/{}?alt=media", audio_file.id),
                                    md5_hash: audio_file.md5_checksum.clone(),
                                    album_id: folder.id.clone(),
                                    cover_url: cover_url.clone(),
                                    is_cue: true,
                                    disc_number: 0,
                                    track_number: track.track_number,
                                    start_offset_ms: track.start_ms,
                                    local_path: None,
                                    download_status: DownloadStatus::None,
                                });
                            }
                        }
                    }
                }
            }
        }
    } else {
        for file in files {
            if is_audio_file(&file.name) {
                let (disc, track, title) = parse_filename(&file.name);
                songs.push(Song {
                    id: file.id.clone(),
                    title: if title.is_empty() { file.name.clone() } else { title },
                    artist: folder_artist.clone(),
                    album: folder_album.clone(),
                    duration_ms: 0,
                    size: file.size.as_deref().unwrap_or("0").parse().unwrap_or(0),
                    data_url: format!("https://www.googleapis.com/drive/v3/files/{}?alt=media", file.id),
                    md5_hash: file.md5_checksum.clone(),
                    album_id: folder.id.clone(),
                    cover_url: cover_url.clone(),
                    is_cue: false,
                    disc_number: disc,
                    track_number: track,
                    start_offset_ms: 0,
                    local_path: None,
                    download_status: DownloadStatus::None,
                });
            }
        }
    }
    Ok(songs)
}

fn parse_folder_name(name: &str) -> (String, String) {
    let parts: Vec<&str> = name.split(" - ").collect();
    if parts.len() == 2 { (parts[0].trim().to_string(), parts[1].trim().to_string()) } else { ("Various Artists".to_string(), name.trim().to_string()) }
}

fn is_audio_file(name: &str) -> bool {
    let n = name.to_lowercase();
    n.ends_with(".mp3") || n.ends_with(".flac") || n.ends_with(".wav") || n.ends_with(".m4a")
}

fn parse_filename(name: &str) -> (u32, u32, String) {
    let clean_name = name.rsplit_once('.').map(|(base, _)| base).unwrap_or(name);
    if let Some(dash_idx) = clean_name.find('-') {
        if dash_idx > 0 && dash_idx < 3 {
            let disc_part = &clean_name[..dash_idx];
            if let Ok(disc) = disc_part.parse::<u32>() {
                let rest = clean_name[dash_idx+1..].trim();
                if let Some(space_idx) = rest.find(' ') {
                    let track_part = &rest[..space_idx];
                    if let Ok(track) = track_part.parse::<u32>() {
                        return (disc, track, rest[space_idx+1..].trim().to_string());
                    }
                }
            }
        }
    }
    if let Some(space_idx) = clean_name.find(' ') {
        let track_part = &clean_name[..space_idx];
        if let Ok(track) = track_part.parse::<u32>() {
            return (0, track, clean_name[space_idx+1..].trim().to_string());
        }
    }
    (0, 0, clean_name.to_string())
}
