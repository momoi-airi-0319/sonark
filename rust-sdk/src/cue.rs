
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
            current_file = extract_value(trimmed, "FILE");
        } else if trimmed.starts_with("PERFORMER") {
            let val = extract_value(trimmed, "PERFORMER");
            if current_track_num.is_none() {
                album_artist = val;
            } else {
                current_track_artist = val;
            }
        } else if trimmed.starts_with("TITLE") {
            let val = extract_value(trimmed, "TITLE");
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

fn extract_value(line: &str, key: &str) -> String {
    if let Some(first_quote) = line.find('"') {
        if let Some(last_quote) = line.rfind('"') {
            if first_quote != last_quote {
                return line[first_quote + 1..last_quote].to_string();
            }
        }
    }
    // Fallback for no quotes
    line[key.len()..].trim().to_string()
}

fn parse_time_to_ms(time_str: &str) -> u64 {
    let parts: Vec<&str> = time_str.split(':').collect();
    if parts.len() != 3 { return 0; }

    let mins: u64 = parts[0].parse().unwrap_or(0);
    let secs: u64 = parts[1].parse().unwrap_or(0);
    let frames: u64 = parts[2].parse().unwrap_or(0);

    (mins * 60 * 1000) + (secs * 1000) + (frames * 1000 / 75)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_time_to_ms() {
        assert_eq!(parse_time_to_ms("00:00:00"), 0);
        assert_eq!(parse_time_to_ms("01:02:00"), 62000); // 1 min 2 sec
        assert_eq!(parse_time_to_ms("00:00:37"), 493);   // 37 frames (37 * 1000 / 75 = 493.33)
        assert_eq!(parse_time_to_ms("10:00:74"), 600986); // 10 min 0 sec 74 frames
        assert_eq!(parse_time_to_ms("invalid"), 0);
    }

    #[test]
    fn test_parse_standard_cue() {
        let content = r#"
            REM COMMENT
            PERFORMER "Artist Name"
            TITLE "Album Title"
            FILE "audio_file.flac" WAVE
              TRACK 01 AUDIO
                TITLE "First Track"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second Track"
                PERFORMER "Special Guest"
                INDEX 01 05:30:15
        "#;

        let sheet = parse_cue(content).expect("Should parse valid CUE");
        assert_eq!(sheet.album, "Album Title");
        assert_eq!(sheet.album_artist, "Artist Name");
        assert_eq!(sheet.file_name, "audio_file.flac");
        assert_eq!(sheet.tracks.len(), 2);

        // Track 1: Inherits album artist
        assert_eq!(sheet.tracks[0].track_number, 1);
        assert_eq!(sheet.tracks[0].title, "First Track");
        assert_eq!(sheet.tracks[0].artist, "Artist Name");
        assert_eq!(sheet.tracks[0].start_ms, 0);

        // Track 2: Overrides album artist
        assert_eq!(sheet.tracks[1].track_number, 2);
        assert_eq!(sheet.tracks[1].title, "Second Track");
        assert_eq!(sheet.tracks[1].artist, "Special Guest");
        assert_eq!(sheet.tracks[1].start_ms, 330200); // 5*60*1000 + 30*1000 + 15*1000/75 = 330200
    }

    #[test]
    fn test_parse_cue_without_quotes() {
        let content = r#"
            TITLE Album Without Quotes
            TRACK 01 AUDIO
              TITLE Track Without Quotes
              INDEX 01 00:01:00
        "#;
        let sheet = parse_cue(content).unwrap();
        assert_eq!(sheet.album, "Album Without Quotes");
        assert_eq!(sheet.tracks[0].title, "Track Without Quotes");
    }

    #[test]
    fn test_multiple_index_markers() {
        let content = r#"
            TRACK 01 AUDIO
              INDEX 00 00:00:00
              INDEX 01 00:00:10
        "#;
        let sheet = parse_cue(content).unwrap();
        // Current logic associates the first INDEX 01 with the track.
        assert_eq!(sheet.tracks[0].start_ms, 133); // 10 frames = 133ms
    }

    #[test]
    fn test_invalid_cue_returns_none() {
        assert!(parse_cue("random text").is_none());
        assert!(parse_cue("TITLE \"Only Album\"").is_none()); // No TRACK
    }
}

