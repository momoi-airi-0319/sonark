use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Song {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub size: u64,
    pub data_url: String,
    pub md5_hash: Option<String>,
    pub album_id: String,
    pub cover_url: Option<String>,
    pub is_cue: bool,
    pub disc_number: u32,
    pub track_number: u32,
    pub start_offset_ms: u64,
    pub local_path: Option<String>,
    pub download_status: DownloadStatus,
}

#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub enum DownloadStatus {
    None,
    Pending,
    Downloading,
    Completed,
    Paused,
    Error,
}

impl From<&str> for DownloadStatus {
    fn from(s: &str) -> Self {
        match s {
            "Pending" => DownloadStatus::Pending,
            "Downloading" => DownloadStatus::Downloading,
            "Completed" => DownloadStatus::Completed,
            "Paused" => DownloadStatus::Paused,
            "Error" => DownloadStatus::Error,
            _ => DownloadStatus::None,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Artist {
    pub name: String,
    pub album_count: u32,
    pub song_count: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Album {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub cover_url: Option<String>,
    pub local_cover_path: Option<String>,
    pub song_count: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LibraryStats {
    pub total_songs: u32,
    pub total_albums: u32,
    pub total_artists: u32,
    pub total_size_bytes: u64,
    pub last_sync_time: String,
}

#[derive(Clone, Debug)]
pub struct DownloadProgress {
    pub song_id: String,
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub status: DownloadStatus,
}
