# Implementation Plan - Google Drive Only Refactor

Refactor Sonark to remove local storage support and implement music loading from a "Vault" directory on Google Drive.

## Proposed Changes

### 1. Remove Local Storage
- **[MODIFY] [AndroidManifest.xml](file:///C:/Users/airi/wksp/Sonark/app/src/main/AndroidManifest.xml)**: Remove `READ_EXTERNAL_STORAGE` and `READ_MEDIA_AUDIO` permissions.
- **[MODIFY] [MusicRepository.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/repository/MusicRepository.kt)**:
    - Remove `getLocalSongs` logic.
    - Implement `getDriveSongs` with folder traversal for `Vault/` structure.
- **[MODIFY] [SettingsScreen.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/SettingsScreen.kt)**: Remove "Local Storage" toggle.
- **[MODIFY] [SettingsViewModel.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/SettingsViewModel.kt)**: Remove local storage related state/methods.
- **[MODIFY] [SettingsRepository.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/repository/SettingsRepository.kt)**: Remove local storage preference logic.
- **[MODIFY] [MainActivity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/MainActivity.kt)**: Remove permission request logic.
- **[MODIFY] [MainViewModel.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/MainViewModel.kt)**: Update `loadSongs` to only call `getDriveSongs`.

### 2. Google Drive Integration
- **[MODIFY] [MusicRepository.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/repository/MusicRepository.kt)**:
    - Add Google Drive Service initialization.
    - Implement traversal: `Vault/` -> `Album Folders` -> `Songs`.
    - Parse song titles from filenames (e.g., "01 - Song Title").
    - Find `cover.jpg` for album art.
- **[NEW] [DriveHelper.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/remote/DriveHelper.kt)**: Utility to handle Drive API interactions.

### 3. Documentation
- **[MODIFY] [README.md](file:///C:/Users/airi/wksp/Sonark/README.md)**: Update with Google Drive focus and Vault structure documentation.

## Verification Plan

### Automated Tests
- Build the project: `./gradlew assembleDebug`

### Manual Verification
- Verify permissions are not requested.
- Verify "Local Storage" toggle is gone from Settings.
- Verify "Vault" structure is documented in README.
