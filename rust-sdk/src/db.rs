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
                download_status: DownloadStatus::None, // Simplified for brevity
            })
        })?.collect::<Result<Vec<_>, _>>()?;

        Ok(songs)
    }

    pub fn search_songs(&self, query: &str) -> anyhow::Result<Vec<Song>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT * FROM songs WHERE title LIKE ?1 OR artist LIKE ?2 OR album LIKE ?3 ORDER BY album, disc_number, track_number")?;

        let pattern = format!("%{}%", query);
        let songs = stmt.query_map(params![pattern, pattern, pattern], |row| {
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
                download_status: DownloadStatus::None,
            })
        })?.collect::<Result<Vec<_>, _>>()?;

        Ok(songs)
    }
}
