use rusqlite::{params, Connection};
use std::sync::{Arc, Mutex};
use crate::models::*;

pub struct Database {
    conn: Arc<Mutex<Connection>>,
}

impl Database {
    pub fn open(path: &str) -> anyhow::Result<Self> {
        let conn = Connection::open(path)?;

        // Initialize schema
        conn.execute(
            "CREATE TABLE IF NOT EXISTS songs (
                id TEXT PRIMARY KEY,
                title TEXT,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER,
                size INTEGER,
                data_url TEXT,
                md5_hash TEXT,
                album_id TEXT,
                cover_url TEXT,
                is_cue INTEGER,
                disc_number INTEGER,
                track_number INTEGER,
                start_offset_ms INTEGER,
                local_path TEXT,
                download_status TEXT
            )",
            [],
        )?;

        conn.execute(
            "CREATE TABLE IF NOT EXISTS config (
                key TEXT PRIMARY KEY,
                value TEXT
            )",
            [],
        )?;

        // Add indices for performance
        conn.execute("CREATE INDEX IF NOT EXISTS idx_songs_search ON songs (title, artist, album)", [])?;
        conn.execute("CREATE INDEX IF NOT EXISTS idx_songs_album ON songs (album, disc_number, track_number)", [])?;

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    pub fn save_songs(&self, songs: &[Song]) -> anyhow::Result<()> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;

        for song in songs {
            tx.execute(
                "INSERT OR REPLACE INTO songs (
                    id, title, artist, album, duration_ms, size, data_url, md5_hash,
                    album_id, cover_url, is_cue, disc_number, track_number,
                    start_offset_ms, local_path, download_status
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
                params![
                    song.id, song.title, song.artist, song.album, song.duration_ms,
                    song.size, song.data_url, song.md5_hash, song.album_id,
                    song.cover_url, song.is_cue as i32, song.disc_number,
                    song.track_number, song.start_offset_ms, song.local_path,
                    format!("{:?}", song.download_status)
                ],
            )?;
        }

        tx.commit()?;
        Ok(())
    }

    pub fn get_all_songs(&self) -> anyhow::Result<Vec<Song>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT * FROM songs ORDER BY album, disc_number, track_number")?;

        let songs = stmt.query_map([], |row| {
            let status_str: String = row.get(15)?;
            Ok(Song {
                id: row.get(0)?,
                title: row.get(1)?,
                artist: row.get(2)?,
                album: row.get(3)?,
                duration_ms: row.get(4)?,
                size: row.get(5)?,
                data_url: row.get(6)?,
                md5_hash: row.get(7)?,
                album_id: row.get(8)?,
                cover_url: row.get(9)?,
                is_cue: row.get::<_, i32>(10)? != 0,
                disc_number: row.get(11)?,
                track_number: row.get(12)?,
                start_offset_ms: row.get(13)?,
                local_path: row.get(14)?,
                download_status: DownloadStatus::from(status_str.as_str()),
            })
        })?.collect::<Result<Vec<_>, _>>()?;

        Ok(songs)
    }

    pub fn search_songs(&self, query: &str) -> anyhow::Result<Vec<Song>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT * FROM songs WHERE title LIKE ?1 OR artist LIKE ?2 OR album LIKE ?3 ORDER BY album, disc_number, track_number")?;

        let pattern = format!("%{}%", query);
        let songs = stmt.query_map(params![pattern, pattern, pattern], |row| {
            let status_str: String = row.get(15)?;
            Ok(Song {
                id: row.get(0)?,
                title: row.get(1)?,
                artist: row.get(2)?,
                album: row.get(3)?,
                duration_ms: row.get(4)?,
                size: row.get(5)?,
                data_url: row.get(6)?,
                md5_hash: row.get(7)?,
                album_id: row.get(8)?,
                cover_url: row.get(9)?,
                is_cue: row.get::<_, i32>(10)? != 0,
                disc_number: row.get(11)?,
                track_number: row.get(12)?,
                start_offset_ms: row.get(13)?,
                local_path: row.get(14)?,
                download_status: DownloadStatus::from(status_str.as_str()),
            })
        })?.collect::<Result<Vec<_>, _>>()?;

        Ok(songs)
    }

    pub fn set_config(&self, key: &str, value: &str) -> anyhow::Result<()> {
        let conn = self.conn.lock().unwrap();
        self.set_config_internal(&conn, key, value)
    }

    fn set_config_internal(&self, conn: &Connection, key: &str, value: &str) -> anyhow::Result<()> {
        conn.execute(
            "INSERT OR REPLACE INTO config (key, value) VALUES (?1, ?2)",
            params![key, value],
        )?;
        Ok(())
    }

    pub fn get_config(&self, key: &str) -> anyhow::Result<Option<String>> {
        let conn = self.conn.lock().unwrap();
        self.get_config_internal(&conn, key)
    }

    fn get_config_internal(&self, conn: &Connection, key: &str) -> anyhow::Result<Option<String>> {
        let mut stmt = conn.prepare("SELECT value FROM config WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;
        if let Some(row) = rows.next()? {
            Ok(Some(row.get(0)?))
        } else {
            Ok(None)
        }
    }

    pub fn delete_songs_not_in_albums(&self, valid_album_ids: &[String]) -> anyhow::Result<usize> {
        let conn = self.conn.lock().unwrap();
        if valid_album_ids.is_empty() {
            return Ok(0);
        }
        let placeholders: String = valid_album_ids.iter().map(|_| "?").collect::<Vec<_>>().join(",");
        let query = format!("DELETE FROM songs WHERE album_id NOT IN ({})", placeholders);

        let mut stmt = conn.prepare(&query)?;
        let count = stmt.execute(rusqlite::params_from_iter(valid_album_ids))?;
        Ok(count)
    }

    pub fn get_all_albums(&self) -> anyhow::Result<Vec<Album>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT album_id, album, artist, cover_url, COUNT(*)
             FROM songs
             GROUP BY album_id
             ORDER BY artist, album"
        )?;

        let albums = stmt.query_map([], |row| {
            Ok(Album {
                id: row.get(0)?,
                title: row.get(1)?,
                artist: row.get(2)?,
                cover_url: row.get(3)?,
                local_cover_path: None,
                song_count: row.get(4)?,
            })
        })?.collect::<Result<Vec<_>, _>>()?;

        Ok(albums)
    }

    pub fn get_all_artists(&self) -> anyhow::Result<Vec<Artist>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT artist, COUNT(DISTINCT album_id), COUNT(*)
             FROM songs
             GROUP BY artist
             ORDER BY artist"
        )?;

        let artists = stmt.query_map([], |row| {
            Ok(Artist {
                name: row.get(0)?,
                album_count: row.get(1)?,
                song_count: row.get(2)?,
            })
        })?.collect::<Result<Vec<_>, _>>()?;

        Ok(artists)
    }

    pub fn get_songs_for_album(&self, album_id: &str) -> anyhow::Result<Vec<Song>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT * FROM songs WHERE album_id = ?1 ORDER BY disc_number, track_number")?;
        self.map_songs(&mut stmt, params![album_id])
    }

    pub fn get_songs_for_artist(&self, artist: &str) -> anyhow::Result<Vec<Song>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT * FROM songs WHERE artist = ?1 ORDER BY album, disc_number, track_number")?;
        self.map_songs(&mut stmt, params![artist])
    }

    pub fn get_library_stats(&self) -> anyhow::Result<LibraryStats> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT COUNT(*), COUNT(DISTINCT album_id), COUNT(DISTINCT artist), SUM(size) FROM songs")?;
        let mut rows = stmt.query([])?;

        if let Some(row) = rows.next()? {
            let last_sync = self.get_config_internal(&conn, "last_sync_time")?.unwrap_or_default();
            Ok(LibraryStats {
                total_songs: row.get(0)?,
                total_albums: row.get(1)?,
                total_artists: row.get(2)?,
                total_size_bytes: row.get::<_, Option<u64>>(3)?.unwrap_or(0),
                last_sync_time: last_sync,
            })
        } else {
            anyhow::bail!("Stats failed")
        }
    }

    fn map_songs(&self, stmt: &mut rusqlite::Statement, params: impl rusqlite::Params) -> anyhow::Result<Vec<Song>> {
        let songs = stmt.query_map(params, |row| {
            let status_str: String = row.get(15)?;
            Ok(Song {
                id: row.get(0)?,
                title: row.get(1)?,
                artist: row.get(2)?,
                album: row.get(3)?,
                duration_ms: row.get(4)?,
                size: row.get(5)?,
                data_url: row.get(6)?,
                md5_hash: row.get(7)?,
                album_id: row.get(8)?,
                cover_url: row.get(9)?,
                is_cue: row.get::<_, i32>(10)? != 0,
                disc_number: row.get(11)?,
                track_number: row.get(12)?,
                start_offset_ms: row.get(13)?,
                local_path: row.get(14)?,
                download_status: DownloadStatus::from(status_str.as_str()),
            })
        })?.collect::<Result<Vec<_>, _>>()?;
        Ok(songs)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::{Song, DownloadStatus};

    fn setup_db() -> Database {
        Database::open(":memory:").unwrap()
    }

    fn create_mock_song(id: &str, title: &str, album: &str) -> Song {
        Song {
            id: id.to_string(),
            title: title.to_string(),
            artist: "Test Artist".to_string(),
            album: album.to_string(),
            duration_ms: 300000,
            size: 1024 * 1024,
            data_url: "http://example.com".to_string(),
            md5_hash: Some("hash".to_string()),
            album_id: "album_id".to_string(),
            cover_url: None,
            is_cue: false,
            disc_number: 1,
            track_number: 1,
            start_offset_ms: 0,
            local_path: None,
            download_status: DownloadStatus::None,
        }
    }

    #[test]
    fn test_save_and_get_songs_with_status() {
        let db = setup_db();
        let mut song = create_mock_song("1", "Song A", "Album X");
        song.download_status = DownloadStatus::Completed;
        song.local_path = Some("/path/to/file".to_string());
        song.duration_ms = 45000;

        db.save_songs(&[song.clone()]).unwrap();

        let all_songs = db.get_all_songs().unwrap();
        assert_eq!(all_songs.len(), 1);
        let s = &all_songs[0];
        assert_eq!(s.id, "1");
        assert_eq!(s.download_status, DownloadStatus::Completed);
        assert_eq!(s.local_path, Some("/path/to/file".to_string()));
        assert_eq!(s.duration_ms, 45000);
    }

    #[test]
    fn test_update_existing_song() {
        let db = setup_db();
        let mut song = create_mock_song("1", "Old Title", "Album");
        db.save_songs(&[song.clone()]).unwrap();

        song.title = "New Title".to_string();
        db.save_songs(&[song.clone()]).unwrap();

        let all_songs = db.get_all_songs().unwrap();
        assert_eq!(all_songs.len(), 1);
        assert_eq!(all_songs[0].title, "New Title");
    }

    #[test]
    fn test_search_songs() {
        let db = setup_db();
        db.save_songs(&[
            create_mock_song("1", "Yellow Submarine", "The Beatles"),
            create_mock_song("2", "Bohemian Rhapsody", "Queen"),
            create_mock_song("3", "Let It Be", "The Beatles"),
        ]).unwrap();

        let results = db.search_songs("Yellow").unwrap();
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].id, "1");

        let results = db.search_songs("Beatles").unwrap();
        assert_eq!(results.len(), 2);
    }

    #[test]
    fn test_aggregates_albums_and_artists() {
        let db = setup_db();
        let mut s1 = create_mock_song("1", "S1", "Album 1");
        s1.artist = "Artist A".to_string();
        s1.album_id = "a1".to_string();
        s1.size = 100;

        let mut s2 = create_mock_song("2", "S2", "Album 1");
        s2.artist = "Artist A".to_string();
        s2.album_id = "a1".to_string();
        s2.size = 200;

        let mut s3 = create_mock_song("3", "S3", "Album 2");
        s3.artist = "Artist B".to_string();
        s3.album_id = "a2".to_string();
        s3.size = 300;

        db.save_songs(&[s1, s2, s3]).unwrap();

        // Test Albums
        let albums = db.get_all_albums().unwrap();
        assert_eq!(albums.len(), 2);
        let a1 = albums.iter().find(|a| a.id == "a1").unwrap();
        assert_eq!(a1.song_count, 2);
        assert_eq!(a1.artist, "Artist A");

        // Test Artists
        let artists = db.get_all_artists().unwrap();
        assert_eq!(artists.len(), 2);
        let art_a = artists.iter().find(|a| a.name == "Artist A").unwrap();
        assert_eq!(art_a.album_count, 1);
        assert_eq!(art_a.song_count, 2);

        // Test Library Stats
        let stats = db.get_library_stats().unwrap();
        assert_eq!(stats.total_songs, 3);
        assert_eq!(stats.total_albums, 2);
        assert_eq!(stats.total_artists, 2);
        assert_eq!(stats.total_size_bytes, 600);

        // Test Songs for Album
        let album_songs = db.get_songs_for_album("a1").unwrap();
        assert_eq!(album_songs.len(), 2);
        assert!(album_songs.iter().all(|s| s.album_id == "a1"));
    }

    #[test]
    fn test_delete_cleanup() {
        let db = setup_db();
        let mut s1 = create_mock_song("1", "S1", "A1");
        s1.album_id = "A1".to_string();
        let mut s2 = create_mock_song("2", "S2", "A2");
        s2.album_id = "A2".to_string();
        db.save_songs(&[s1, s2]).unwrap();

        // Keep only A1
        db.delete_songs_not_in_albums(&["A1".to_string()]).unwrap();

        let all = db.get_all_songs().unwrap();
        assert_eq!(all.len(), 1);
        assert_eq!(all[0].album_id, "A1");
    }
}

