use crate::Song;

pub struct CueTrack {
    pub title: String,
    pub artist: String,
    pub track_number: u32,
    pub start_ms: u64,
}

pub struct CueSheet {
    pub album: String,
    pub album_artist: String,
    pub tracks: Vec<CueTrack>,
    pub file_name: String,
}

pub fn parse_cue(content: &str) -> Option<CueSheet> {
    let mut album = String::from("Unknown Album");
    let mut album_artist = String::from("Various Artists");
    let mut tracks = Vec::new();
    let mut current_file = String::new();

    let mut current_track_num: Option<u32> = None;
    let mut current_track_title = String::new();
    let mut current_track_artist = String::new();

    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("FILE") {
            current_file = trimmed.split('"').nth(1).unwrap_or("").to_string();
        } else if trimmed.starts_with("PERFORMER") {
            let val = trimmed.split('"').nth(1).unwrap_or("").to_string();
            if current_track_num.is_none() {
                album_artist = val;
            } else {
                current_track_artist = val;
            }
        } else if trimmed.starts_with("TITLE") {
            let val = trimmed.split('"').nth(1).unwrap_or("").to_string();
            if current_track_num.is_none() {
                album = val;
            } else {
                current_track_title = val;
            }
        } else if trimmed.starts_with("TRACK") {
            // Save previous track if any
            push_track(&mut tracks, &mut current_track_num, &mut current_track_title, &mut current_track_artist, 0);

            let parts: Vec<&str> = trimmed.split_whitespace().collect();
            if parts.len() >= 2 {
                current_track_num = parts[1].parse().ok();
                current_track_artist = album_artist.clone();
            }
        } else if trimmed.starts_with("INDEX 01") {
            let time_str = trimmed.split_whitespace().last().unwrap_or("00:00:00");
            let start_ms = parse_time_to_ms(time_str);
            // This index marker belongs to the *current* track being defined
            if let Some(mut last) = tracks.last_mut() {
                 // Optimization: if INDEX 01 comes after TRACK, update the last one
                 // But in standard CUE, it's TRACK -> INDEX
            }
            // For simplicity in this implementation, we associate index with the number we just parsed
            push_track(&mut tracks, &mut current_track_num, &mut current_track_title, &mut current_track_artist, start_ms);
        }
    }

    if tracks.is_empty() { return None; }

    Some(CueSheet {
        album,
        album_artist,
        tracks,
        file_name: current_file,
    })
}

fn push_track(tracks: &mut Vec<CueTrack>, num: &mut Option<u32>, title: &mut String, artist: &mut String, start: u64) {
    if let Some(n) = *num {
        tracks.push(CueTrack {
            title: title.clone(),
            artist: artist.clone(),
            track_number: n,
            start_ms: start,
        });
        *num = None;
        title.clear();
    }
}

fn parse_time_to_ms(time_str: &str) -> u64 {
    let parts: Vec<&str> = time_str.split(':').collect();
    if parts.len() != 3 { return 0; }

    let mins: u64 = parts[0].parse().unwrap_or(0);
    let secs: u64 = parts[1].parse().unwrap_or(0);
    let frames: u64 = parts[2].parse().unwrap_or(0);

    (mins * 60 * 1000) + (secs * 1000) + (frames * 1000 / 75)
}
