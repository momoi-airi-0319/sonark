# Multi-disc Album Support Walkthrough

Successfully implemented support for albums with multiple discs.

## Changes Made

### Data Layer
- **Song Model**: Added `discNumber` and `trackNumber` to `Song` and `SongEntity`.
- **Album Model**: Added a `discs` property that automatically groups songs by their disc number.
- **Database**: Bumped version to 7 to accommodate new fields.

### Logic
- **DriveMusicProvider**:
    - Implemented filename parsing for `[Disc]-[Track] - [Title]` format.
    - Added support for multiple `.cue` files in a single album folder, treating each as a disc.
    - Defaulted tracks without a disc prefix to "Disc 0".

### UI
- **AlbumScreen**: Now displays "Disc X" headers when an album has multiple discs or an explicit disc number.
- **SongListItem**: Reverted to showing only the song title (removed track number prefix) to keep the list clean.

### Documentation
- Updated `README.md` with instructions on how to organize multi-disc albums on Google Drive.

## Verification
- Verified code structure and basic logic.
- Manual verification on device is recommended with the following structure:
    - `Vault/Sample Album/1-01 - Disc1Track1.mp3`
    - `Vault/Sample Album/2-01 - Disc2Track1.mp3`
