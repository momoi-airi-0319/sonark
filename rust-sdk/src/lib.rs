uniffi::setup_scaffolding!();

pub mod api;
pub mod cue;
pub mod models;
pub mod db;

use crate::api::{SonarkEngine as CoreEngine, SonarkObserver, AuthProvider};
use crate::models::*;
use std::sync::Arc;

#[uniffi::export]
pub fn get_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

// Wrapper for UniFFI
#[derive(uniffi::Object)]
pub struct SonarkEngine {
    core: Arc<CoreEngine>,
}

#[uniffi::export]
impl SonarkEngine {
    #[uniffi::constructor]
    pub fn new(db_path: String) -> Result<Self, SonarkError> {
        #[cfg(target_os = "android")]
        {
            #[cfg(feature = "android")]
            let _ = android_logger::init_once(
                android_logger::Config::default()
                    .with_tag("SonarkSDK")
                    .with_max_level(log::LevelFilter::Debug),
            );
        }

        Ok(Self {
            core: Arc::new(CoreEngine::new(db_path).map_err(|e| SonarkError::DatabaseError { message: e.to_string() })?),
        })
    }

    pub fn set_observer(&self, observer: Box<dyn SonarkObserver>) {
        self.core.set_observer(observer);
    }

    pub fn set_auth_provider(&self, auth_provider: Box<dyn AuthProvider>) {
        self.core.set_auth_provider(auth_provider);
    }

    pub fn get_all_songs(&self) -> Vec<Song> {
        self.core.get_all_songs()
    }

    pub fn search(&self, query: String) -> Vec<Song> {
        self.core.search(query)
    }

    pub fn get_all_albums(&self) -> Vec<Album> {
        self.core.get_all_albums()
    }

    pub fn get_all_artists(&self) -> Vec<Artist> {
        self.core.get_all_artists()
    }

    pub fn get_songs_for_album(&self, album_id: String) -> Vec<Song> {
        self.core.get_songs_for_album(album_id)
    }

    pub fn get_songs_for_artist(&self, artist: String) -> Vec<Song> {
        self.core.get_songs_for_artist(artist)
    }

    pub fn get_library_stats(&self) -> LibraryStats {
        self.core.get_library_stats()
    }

    pub fn sync_library(&self) {
        log::error!("DEBUG: sync_library called on wrapper");
        self.core.sync_library();
    }

    pub fn start_download(&self, song_id: String, url: String, destination: String) {
        self.core.start_download(song_id, url, destination);
    }

    pub fn scan_local_metadata(&self, song_id: String, path: String) -> Option<Song> {
        self.core.scan_local_metadata(song_id, path)
    }
}

#[cfg(test)]
mod tests {
    // Unit tests will go here
}
