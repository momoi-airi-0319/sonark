use clap::{Parser, Subcommand};
use std::sync::mpsc;
use std::time::Duration;

// If you want to use the library from a binary in the same crate,
// you usually use the crate name.
use uniffi_sonark_sdk::api::{SonarkEngine, SonarkObserver, AuthProvider};
use uniffi_sonark_sdk::models::{Song, DownloadProgress};

#[derive(Parser)]
#[command(author, version, about, long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Commands,

    #[arg(short, long, default_value = "sonark.db")]
    db: String,

    #[arg(short, long)]
    token: Option<String>,
}

#[derive(Subcommand)]
enum Commands {
    /// Synchronize library with Google Drive
    Sync,
    /// List all songs, albums, or artists
    List {
        #[arg(value_enum)]
        category: Category,
    },
    /// Search for songs
    Search {
        query: String,
    },
    /// Download a song
    Download {
        song_id: String,
        url: String,
        dest: String,
    },
    /// Show library statistics
    Stats,
}

#[derive(clap::ValueEnum, Clone)]
enum Category {
    Songs,
    Albums,
    Artists,
}

struct CliObserver {
    sync_tx: mpsc::Sender<Result<Vec<Song>, String>>,
}

impl SonarkObserver for CliObserver {
    fn on_download_progress(&self, progress: DownloadProgress) {
        println!("Download progress: {} - {}/{}", progress.song_id, progress.downloaded_bytes, progress.total_bytes);
    }
    fn on_sync_complete(&self, songs: Vec<Song>) {
        let _ = self.sync_tx.send(Ok(songs));
    }
    fn on_error(&self, message: String) {
        let _ = self.sync_tx.send(Err(message));
    }
}

struct StaticAuthProvider {
    token: String,
}

impl AuthProvider for StaticAuthProvider {
    fn get_access_token(&self) -> String {
        self.token.clone()
    }
}

fn main() -> anyhow::Result<()> {
    env_logger::init();
    let cli = Cli::parse();

    let engine = SonarkEngine::new(cli.db);

    let (sync_tx, sync_rx) = mpsc::channel();
    engine.set_observer(Box::new(CliObserver { sync_tx }));

    let token = if let Some(t) = cli.token {
        t
    } else {
        // Try to read from token.txt in parent directory if not provided
        std::fs::read_to_string("../token.txt")
            .unwrap_or_default()
            .lines()
            .nth(2) // The token is on the 3rd line based on read_file result
            .unwrap_or_default()
            .to_string()
    };

    if !token.is_empty() {
        engine.set_auth_provider(Box::new(StaticAuthProvider { token }));
    }

    match cli.command {
        Commands::Sync => {
            println!("Starting sync...");
            engine.sync_library();
            match sync_rx.recv_timeout(Duration::from_secs(300)) {
                Ok(Ok(songs)) => println!("Sync complete! Found {} songs.", songs.len()),
                Ok(Err(e)) => eprintln!("Sync error: {}", e),
                Err(_) => eprintln!("Sync timed out."),
            }
        }
        Commands::List { category } => {
            match category {
                Category::Songs => {
                    for song in engine.get_all_songs() {
                        println!("{} - {} ({})", song.artist, song.title, song.album);
                    }
                }
                Category::Albums => {
                    for album in engine.get_all_albums() {
                        println!("{} - {} ({} songs)", album.artist, album.title, album.song_count);
                    }
                }
                Category::Artists => {
                    for artist in engine.get_all_artists() {
                        println!("{} ({} albums, {} songs)", artist.name, artist.album_count, artist.song_count);
                    }
                }
            }
        }
        Commands::Search { query } => {
            for song in engine.search(query) {
                println!("{} - {} ({}) [ID: {}]", song.artist, song.title, song.album, song.id);
            }
        }
        Commands::Download { song_id, url, dest } => {
            println!("Starting download to {}...", dest);
            engine.start_download(song_id, url, dest);
            // In a real CLI we might want to wait for completion,
            // but the SDK uses an async background task.
            // For now, we just wait a bit or use a more sophisticated way to track progress.
            std::thread::sleep(Duration::from_secs(10));
        }
        Commands::Stats => {
            let stats = engine.get_library_stats();
            println!("Total Songs: {}", stats.total_songs);
            println!("Total Albums: {}", stats.total_albums);
            println!("Total Artists: {}", stats.total_artists);
            println!("Total Size: {} bytes", stats.total_size_bytes);
            println!("Last Sync: {}", stats.last_sync_time);
        }
    }

    Ok(())
}
