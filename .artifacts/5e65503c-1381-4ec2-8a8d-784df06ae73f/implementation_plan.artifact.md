# Support for Multi-disc Albums

This plan introduces a "Disc" abstraction to better handle albums with multiple discs, which is common for larger albums or those archived on platforms like the Internet Archive.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [Song.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/model/Song.kt)
- Add `discNumber: Int` and `trackNumber: Int` to the `Song` data class.
- Add a `Disc` data class to group songs by disc.

#### [MODIFY] [SongEntity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/database/SongEntity.kt)
- Add `discNumber` and `trackNumber` fields to `SongEntity`.
- Update `toSong`, `toSyncSong`, and `fromSyncSong` to include these fields.

#### [MODIFY] [SonarkDatabase.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/database/SonarkDatabase.kt)
- Increment the database version to 7 (or next available) as we added fields to `SongEntity`.

#### [MODIFY] [Album.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/model/Album.kt)
- Add a `discs` property to the `Album` class that groups `songs` into `Disc` objects.

### [Music Providers]

#### [MODIFY] [DriveMusicProvider.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/provider/DriveMusicProvider.kt)
- Update `syncLibrary` to recursively look for music files or at least handle one level of subfolders (e.g., "Disc 1", "Disc 2").
- Implement logic to extract `discNumber` from folder names and `trackNumber` from file names.
- Update `parseSong` and `parseCueAlbum` to populate these new fields.

### [UI Layer]

#### [MODIFY] [AlbumScreen.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/AlbumScreen.kt)
- Update the album page to display disc headers if the album has more than one disc.
- Group songs by disc in the `LazyColumn`.

#### [MODIFY] [SongListItem.kt](file:///C:/Users/airi/wksp/Sonark/ui/components/SongListItem.kt)
- (Optional) Update to display the track number if available.

### [Documentation]

#### [MODIFY] [README.md](file:///C:/Users/airi/wksp/Sonark/README.md)
- Update the features section to mention multi-disc album support.

## Verification Plan

### Manual Verification
1.  Upload a multi-disc album to Google Drive (e.g., `Vault/Album Name/Disc 1/01.mp3`, `Vault/Album Name/Disc 2/01.mp3`).
2.  Trigger a sync in the app.
3.  Navigate to the album page and verify that "Disc 1" and "Disc 2" headers are shown and songs are correctly grouped.
4.  Verify that track numbers (if implemented in UI) are correct.
