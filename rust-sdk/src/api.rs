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

#[uniffi::export(callback_interface)]
pub trait SonarkObserver: Send + Sync {
    fn on_download_progress(&self, progress: DownloadProgress);
    fn on_sync_complete(&self, songs: Vec<Song>);
    fn on_error(&self, message: String);
}

#[uniffi::export(callback_interface)]
pub trait AuthProvider: Send + Sync {
    fn get_access_token(&self) -> String;
}

pub struct DownloadManager {
    pub(crate) client: Client,
    pub(crate) active_jobs: Arc<AsyncMutex<HashMap<String, tokio::task::JoinHandle<()>>>>,
}

impl DownloadManager {
    pub fn new() -> Self {
        Self {
            client: Client::builder()
                .user_agent("Sonark-SDK/0.1.0")
                .tcp_keepalive(Some(std::time::Duration::from_secs(60)))
                .build()
                .unwrap(),
            active_jobs: Arc::new(AsyncMutex::new(HashMap::new())),
        }
    }
}

pub struct SonarkEngine {
    pub(crate) download_manager: Arc<DownloadManager>,
    pub(crate) db: Arc<Database>,
    pub(crate) observer: Arc<StdMutex<Option<Box<dyn SonarkObserver>>>>,
    pub(crate) auth_provider: Arc<StdMutex<Option<Box<dyn AuthProvider>>>>,
    pub(crate) base_url: String,
    pub(crate) runtime_handle: tokio::runtime::Handle,
    _runtime: Option<tokio::runtime::Runtime>,
}

impl SonarkEngine {
    pub fn new(db_path: String) -> anyhow::Result<Self> {
        Self::with_base_url(db_path, "https://www.googleapis.com".to_string())
    }

    pub fn with_base_url(db_path: String, base_url: String) -> anyhow::Result<Self> {
        let (handle, rt) = match tokio::runtime::Handle::try_current() {
            Ok(h) => (h, None),
            Err(_) => {
                let rt = tokio::runtime::Builder::new_multi_thread()
                    .enable_all()
                    .build()
                    .map_err(|e| anyhow::anyhow!("Failed to create Tokio runtime: {}", e))?;
                (rt.handle().clone(), Some(rt))
            }
        };

        let db = Database::open(&db_path).map_err(|e| anyhow::anyhow!("Failed to open database at {}: {}", db_path, e))?;

        Ok(Self {
            download_manager: Arc::new(DownloadManager::new()),
            db: Arc::new(db),
            observer: Arc::new(StdMutex::new(None)),
            auth_provider: Arc::new(StdMutex::new(None)),
            base_url,
            runtime_handle: handle,
            _runtime: rt,
        })
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

    pub fn get_all_albums(&self) -> Vec<Album> {
        self.db.get_all_albums().unwrap_or_default()
    }

    pub fn get_all_artists(&self) -> Vec<Artist> {
        self.db.get_all_artists().unwrap_or_default()
    }

    pub fn get_songs_for_album(&self, album_id: String) -> Vec<Song> {
        self.db.get_songs_for_album(&album_id).unwrap_or_default()
    }

    pub fn get_songs_for_artist(&self, artist: String) -> Vec<Song> {
        self.db.get_songs_for_artist(&artist).unwrap_or_default()
    }

    pub fn get_library_stats(&self) -> LibraryStats {
        self.db.get_library_stats().unwrap_or_else(|_| LibraryStats {
            total_songs: 0,
            total_albums: 0,
            total_artists: 0,
            total_size_bytes: 0,
            last_sync_time: "".to_string(),
        })
    }

    pub fn sync_library(&self) {
        log::info!("sync_library called");
        let observer = self.observer.clone();
        let client = self.download_manager.client.clone();
        let auth = self.auth_provider.clone();
        let db = self.db.clone();
        let base_url = self.base_url.clone();

        self.runtime_handle.spawn(async move {
            log::error!("DEBUG: async sync task started");
            let token = {
                let lock = auth.lock().unwrap();
                lock.as_ref().map(|a| a.get_access_token()).unwrap_or_default()
            };
            log::error!("DEBUG: got token: {}", if token.is_empty() { "EMPTY" } else { "VALID" });

            if token.is_empty() {
                let obs = observer.lock().unwrap();
                if let Some(ref o) = *obs {
                    o.on_error("No auth token available".to_string());
                }
                return;
            }

            let result = async {
                let last_sync = db.get_config("last_sync_time")?.unwrap_or_default();

                let vault_id = find_folder(&client, &token, "Vault", "root", &base_url).await?
                    .ok_or_else(|| anyhow::anyhow!("Vault not found"))?;

                let all_album_folders = list_all_files(&client, &token, &vault_id, Some("mimeType = 'application/vnd.google-apps.folder'"), &base_url).await?;
                log::info!("Found {} album folders", all_album_folders.len());

                let mut valid_album_ids = Vec::new();
                let mut folder_tasks = FuturesUnordered::new();

                for folder in all_album_folders {
                    valid_album_ids.push(folder.id.clone());

                    let folder_modified = folder.modified_time.as_deref().unwrap_or_default();
                    if folder_modified > last_sync.as_str() {
                        let c = client.clone();
                        let t = token.clone();
                        let b = base_url.clone();
                        folder_tasks.push(async move { process_album(&c, &t, folder, &b).await });
                    }
                }

                if !folder_tasks.is_empty() {
                    let mut all_songs = Vec::new();
                    while let Some(res) = folder_tasks.next().await {
                        if let Ok(mut songs) = res {
                            all_songs.append(&mut songs);
                        }
                    }
                    all_songs.sort_by(|a, b| a.album.cmp(&b.album).then(a.disc_number.cmp(&b.disc_number)).then(a.track_number.cmp(&b.track_number)));
                    db.save_songs(&all_songs)?;
                }

                if !valid_album_ids.is_empty() {
                    db.delete_songs_not_in_albums(&valid_album_ids)?;
                }

                let now = chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true);
                db.set_config("last_sync_time", &now)?;

                Ok::<(), anyhow::Error>(())
            }.await;

            let obs = observer.lock().unwrap();
            if let Some(ref o) = *obs {
                match result {
                    Ok(_) => {
                        let songs = db.get_all_songs().unwrap_or_default();
                        o.on_sync_complete(songs);
                    },
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

        self.runtime_handle.spawn(async move {
            log::error!("DEBUG: async sync task started");
            let token = {
                let lock = auth.lock().unwrap();
                lock.as_ref().map(|a| a.get_access_token()).unwrap_or_default()
            };
            log::error!("DEBUG: got token: {}", if token.is_empty() { "EMPTY" } else { "VALID" });

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
        if !p.exists() {
            log::error!("Scan error: Path does not exist: {}", path);
            return None;
        }

        let mut duration_ms = 0;
        let mut title = None;
        let mut artist = None;
        let mut album = None;

        if let Ok(tagged_file) = lofty::read_from_path(p) {
            let properties = tagged_file.properties();
            duration_ms = properties.duration().as_millis() as u64;

            if let Some(t) = tagged_file.primary_tag().or_else(|| tagged_file.first_tag()) {
                title = t.title().map(|s| s.to_string());
                artist = t.artist().map(|s| s.to_string());
                album = t.album().map(|s| s.to_string());
            }
        } else {
            log::warn!("Scan: Could not read audio tags from {}, using default metadata", path);
        }

        let songs = self.db.get_all_songs().unwrap_or_default();
        if let Some(mut song) = songs.into_iter().find(|s| s.id == song_id) {
            if let Some(t) = title { song.title = t; }
            if let Some(a) = artist { song.artist = a; }
            if let Some(al) = album { song.album = al; }

            if duration_ms > 0 {
                song.duration_ms = duration_ms;
            }
            song.local_path = Some(path);
            song.download_status = DownloadStatus::Completed;

            let _ = self.db.save_songs(&[song.clone()]);
            return Some(song);
        } else {
            log::error!("Scan error: Song ID {} not found in database", song_id);
        }
        None
    }
}

#[derive(Deserialize, Debug, Clone)]
struct DriveFile {
    id: String,
    name: String,
    size: Option<String>,
    #[serde(rename = "md5Checksum")]
    md5_checksum: Option<String>,
    #[serde(rename = "modifiedTime")]
    modified_time: Option<String>,
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
        eprintln!("DEBUG: Fetching URL: {}", url);
        let res = client.get(url).bearer_auth(token).send().await;
        match res {
            Ok(r) if r.status() == StatusCode::TOO_MANY_REQUESTS || r.status().is_server_error() => {
                if retry_count < 3 {
                    retry_count += 1;
                    eprintln!("WARN: API error {}, retrying...", r.status());
                    tokio::time::sleep(std::time::Duration::from_secs(2u64.pow(retry_count))).await;
                    continue;
                }
                anyhow::bail!("API error: status {}", r.status());
            }
            Ok(r) if r.status().is_success() => {
                eprintln!("DEBUG: API success: {}", r.status());
                return Ok(r);
            }
            Ok(r) => {
                let status = r.status();
                let body = r.text().await.unwrap_or_default();
                eprintln!("ERROR: API failed with status {}: {}", status, body);
                anyhow::bail!("API failed with status {}: {}", status, body);
            }
            Err(e) => {
                eprintln!("ERROR: Request failed: {}. URL: {}", e, url);
                anyhow::bail!("Request failed: {}", e);
            }
        }
    }
}

async fn find_folder(client: &Client, token: &str, name: &str, parent_id: &str, base_url: &str) -> anyhow::Result<Option<String>> {
    let q = format!("name = '{}' and mimeType = 'application/vnd.google-apps.folder' and '{}' in parents and trashed = false", name, parent_id);
    let url = format!("{}/drive/v3/files?q={}&fields=files(id,name)", base_url, urlencoding::encode(&q));
    let resp = fetch_with_retry(client, token, &url).await?;
    let text = resp.text().await?;
    eprintln!("DEBUG: find_folder response: {}", text);
    let res: DriveFileList = serde_json::from_str(&text)?;
    Ok(res.files.first().map(|f| f.id.clone()))
}

async fn list_all_files(client: &Client, token: &str, parent_id: &str, extra_q: Option<&str>, base_url: &str) -> anyhow::Result<Vec<DriveFile>> {
    let mut all_files = Vec::new();
    let mut page_token: Option<String> = None;
    loop {
        let mut q = format!("'{}' in parents and trashed = false", parent_id);
        if let Some(extra) = extra_q { q.push_str(" and "); q.push_str(extra); }
        let mut url = format!("{}/drive/v3/files?q={}&fields=nextPageToken,files(id,name,size,md5Checksum,modifiedTime)&pageSize=1000", base_url, urlencoding::encode(&q));
        if let Some(t) = &page_token { url.push_str("&pageToken="); url.push_str(t); }

        let resp = fetch_with_retry(client, token, &url).await?;
        let text = resp.text().await?;
        log::debug!("list_all_files response: {}", text);

        let res: DriveFileList = match serde_json::from_str(&text) {
            Ok(r) => r,
            Err(e) => {
                log::error!("JSON decode error: {}. Body: {}", e, text);
                anyhow::bail!("JSON decode error: {}", e);
            }
        };

        all_files.extend(res.files);
        page_token = res.next_page_token;
        if page_token.is_none() { break; }
    }
    Ok(all_files)
}

async fn process_album(client: &Client, token: &str, folder: DriveFile, base_url: &str) -> anyhow::Result<Vec<Song>> {
    log::debug!("Processing folder: {}", folder.name);
    let (folder_artist, folder_album) = parse_folder_name(&folder.name);
    let files = list_all_files(client, token, &folder.id, None, base_url).await?;
    let cover_file = files.iter().find(|f| { let n = f.name.to_lowercase(); n.starts_with("cover.") || n.starts_with("folder.") });
    let cover_url = cover_file.map(|f| format!("{}/drive/v3/files/{}?alt=media", base_url, f.id));
    let mut songs = Vec::new();
    let cue_files: Vec<&DriveFile> = files.iter().filter(|f| f.name.to_lowercase().ends_with(".cue")).collect();

    if !cue_files.is_empty() {
        for cue_file in cue_files {
            log::info!("Processing CUE: {}", cue_file.name);
            let url = format!("{}/drive/v3/files/{}?alt=media", base_url, cue_file.id);
            let response = client.get(url).bearer_auth(token).send().await
                .map_err(|e| anyhow::anyhow!("Failed to fetch CUE: {}", e))?;

            let content = response.text().await
                .map_err(|e| anyhow::anyhow!("Failed to read CUE content: {}", e))?;

            if let Some(sheet) = crate::cue::parse_cue(&content) {
                if let Some(audio_file) = files.iter().find(|f| f.name.eq_ignore_ascii_case(&sheet.file_name)) {
                    for track in sheet.tracks {
                        songs.push(Song {
                            id: format!("{}_{}", cue_file.id, track.track_number),
                            title: track.title,
                            artist: if track.artist.is_empty() { folder_artist.clone() } else { track.artist },
                            album: sheet.album.clone(),
                            duration_ms: 0,
                            size: audio_file.size.as_deref().unwrap_or("0").parse().unwrap_or(0),
                            data_url: format!("{}/drive/v3/files/{}?alt=media", base_url, audio_file.id),
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
    } else {
        for file in files {
            if is_audio_file(&file.name) {
                let (disc, track, title) = parse_filename(&file.name);
                log::debug!("Parsed filename '{}' -> disc={}, track={}, title={}", file.name, disc, track, title);
                songs.push(Song {
                    id: file.id.clone(),
                    title: if title.is_empty() { file.name.clone() } else { title },
                    artist: folder_artist.clone(),
                    album: folder_album.clone(),
                    duration_ms: 0,
                    size: file.size.as_deref().unwrap_or("0").parse().unwrap_or(0),
                    data_url: format!("{}/drive/v3/files/{}?alt=media", base_url, file.id),
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
    if let Some((artist, album)) = name.split_once(" - ") {
        (artist.trim().to_string(), album.trim().to_string())
    } else {
        ("Various Artists".to_string(), name.trim().to_string())
    }
}

fn is_audio_file(name: &str) -> bool {
    let n = name.to_lowercase();
    n.ends_with(".mp3") || n.ends_with(".flac") || n.ends_with(".wav") || n.ends_with(".m4a")
}

fn parse_filename(name: &str) -> (u32, u32, String) {
    let clean_name = name.rsplit_once('.').map(|(base, _)| base).unwrap_or(name);

    if let Some(dash_idx) = clean_name.find('-') {
        if dash_idx > 0 && dash_idx < 4 {
            let disc_part = &clean_name[..dash_idx];
            if let Ok(disc) = disc_part.parse::<u32>() {
                let rest = clean_name[dash_idx+1..].trim();
                let split_idx = rest.find(|c: char| c == ' ' || c == '.' || c == '-');
                if let Some(idx) = split_idx {
                    if let Ok(track) = rest[..idx].parse::<u32>() {
                        let title = rest[idx+1..].trim_start_matches(|c: char| c == ' ' || c == '-' || c == '.').trim().to_string();
                        return (disc, track, title);
                    }
                }
            }
        }
    }

    let split_idx = clean_name.find(|c: char| c == ' ' || c == '.' || c == '-');
    if let Some(idx) = split_idx {
        if let Ok(track) = clean_name[..idx].parse::<u32>() {
            let title = clean_name[idx+1..].trim_start_matches(|c: char| c == ' ' || c == '-' || c == '.').trim().to_string();
            return (0, track, title);
        }
    }

    (0, 0, clean_name.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_filename_multi_disc() {
        let (disc, track, title) = parse_filename("1-01 - My Song.mp3");
        assert_eq!(disc, 1);
        assert_eq!(track, 1);
        assert_eq!(title, "My Song");
    }

    #[test]
    fn test_parse_filename_with_leading_dash_and_japanese() {
        let (disc, track, title) = parse_filename("01 - 月陽-ツキアカリ-.mp3");
        assert_eq!(disc, 0);
        assert_eq!(track, 1);
        assert_eq!(title, "月陽-ツキアカリ-");
    }

    #[test]
    fn test_engine_init_invalid_path() {
        let result = SonarkEngine::new("/invalid/path/db.sqlite".to_string());
        assert!(result.is_err());
    }
}

#[cfg(test)]
mod engine_integration_tests {
    use super::*;
    use std::sync::mpsc;
    use std::time::Duration;

    struct TestObserver {
        sync_sender: mpsc::Sender<Result<Vec<Song>, String>>,
        progress_sender: mpsc::Sender<DownloadProgress>,
        error_sender: mpsc::Sender<String>,
    }
    impl SonarkObserver for TestObserver {
        fn on_download_progress(&self, p: DownloadProgress) {
            let _ = self.progress_sender.send(p);
        }
        fn on_sync_complete(&self, songs: Vec<Song>) {
            let _ = self.sync_sender.send(Ok(songs));
        }
        fn on_error(&self, msg: String) {
            let _ = self.sync_sender.send(Err(msg.clone()));
            let _ = self.error_sender.send(msg);
        }
    }

    struct EnvAuthProvider { token: String }
    impl AuthProvider for EnvAuthProvider {
        fn get_access_token(&self) -> String { self.token.clone() }
    }

    fn get_test_token() -> String {
        // Look for token.txt in project root
        let project_root = Path::new(env!("CARGO_MANIFEST_DIR")).parent().unwrap();
        let token_file = project_root.join("token.txt");
        let content = std::fs::read_to_string(token_file).expect("token.txt not found in project root");
        content.lines().nth(2).expect("Token not found on 3rd line of token.txt").to_string()
    }

    #[test]
    #[ignore]
    fn test_real_google_drive_sync() {
        env_logger::builder().filter_level(log::LevelFilter::Debug).init();
        let token = get_test_token();
        let engine = SonarkEngine::new(":memory:".to_string()).expect("Failed to create engine");
        let (sync_tx, sync_rx) = mpsc::channel();
        let (prog_tx, _) = mpsc::channel();
        let (err_tx, _) = mpsc::channel();

        engine.set_observer(Box::new(TestObserver {
            sync_sender: sync_tx,
            progress_sender: prog_tx,
            error_sender: err_tx
        }));
        engine.set_auth_provider(Box::new(EnvAuthProvider { token }));

        println!("Starting real library sync...");
        engine.sync_library();

        match sync_rx.recv_timeout(Duration::from_secs(60)) {
            Ok(Ok(songs)) => {
                println!("Sync successful! Found {} songs.", songs.len());
                assert!(!songs.is_empty());
                let db_songs = engine.get_all_songs();
                assert_eq!(db_songs.len(), songs.len());
            }
            Ok(Err(e)) => panic!("Sync error: {}", e),
            Err(_) => panic!("Sync timeout"),
        }
    }
}
