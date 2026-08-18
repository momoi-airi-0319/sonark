# Project Plan

Build a music player, that plays music from a music vault stored in local storage or Google Drive.

## Project Brief

# Project Brief: Sonark Music Player

Sonark is a streamlined music player designed for users who maintain a personal "music vault." The app focuses on seamless access to music stored either locally on the device or remotely in Google Drive, providing a unified playback experience across various Android form factors.

## Features
- **Vault Connectivity**: Seamlessly link to music repositories stored in local device folders or Google Drive accounts.
- **Unified Library Browsing**: A clean interface to explore, search, and organize music files across both local and cloud sources.
- **Advanced Playback Engine**: Robust audio playback with support for standard controls, seek bars, and notification-based playback management.
- **Dynamic Queue Management**: Create and modify playback queues on the fly, with support for shuffle and repeat modes.

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Navigation**: **Jetpack Navigation 3** (state-driven architecture)
- **Adaptive Strategy**: **Compose Material Adaptive** library for optimized layouts across phones, tablets, and foldables.
- **Asynchrony**: Kotlin Coroutines and Flow for reactive data streams.
- **Media Playback**: Jetpack Media3 (ExoPlayer) for high-performance audio handling.
- **Cloud Integration**: Google Drive API for remote storage access.

## Implementation Steps

### Refinement_1_Permissions: Update MainActivity to handle permission results and re-trigger song loading in MainViewModel.
- **Status:** COMPLETED
- **Updates:** Completed Refinement 1:
- **Acceptance Criteria:**
  - MainActivity uses ActivityResultLauncher correctly
  - loadSongs() is called immediately after permission is granted

### Refinement_2_Settings_Sync: Link the 'Local Storage' toggle in Settings to the scanning logic.
- **Status:** COMPLETED
- **Updates:** Completed Refinement 2:
- **Acceptance Criteria:**
  - Toggling off 'Local Storage' clears the library or stops scanning
  - State is persisted and respected by MainViewModel

### Refinement_3_UI_Feedback: Add a loading indicator and an empty state message to LibraryScreen.
- **Status:** COMPLETED
- **Updates:** Completed Refinement 3:
- **Acceptance Criteria:**
  - User sees a CircularProgressIndicator while loading
  - User sees 'No songs found' message when the list is empty

### Refinement_4_Final_Verification: Final verification of the auto-scan flow using critic_agent.
- **Status:** COMPLETED
- **Updates:** Final verification successful:
- Permission flow is now robust and re-triggers scans correctly.
- Loading and Empty states are present and polished.
- Settings toggle is correctly linked to the music scan logic.
- UI is adaptive across devices.
All local storage auto-scan issues have been resolved.
- **Acceptance Criteria:**
  - App correctly scans and displays music immediately after permission is granted
  - UI feedback is present
- **Duration:** N/A

