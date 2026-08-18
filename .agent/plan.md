# Project Plan

Sonark Music Player - Google Drive Only version with specific vault structure.

## Project Brief

# Sonark Music Player - Project Brief

## Features
*   **Google Drive Cloud Integration**: Securely authenticate and connect to the user's Google Drive to access the specific `Vault/` directory structure.
*   **Dynamic Album Browser**: Automatically scan and display albums based on the `Vault/Album Title/` folder hierarchy, utilizing adaptive layouts for various screen sizes.
*   **Seamless Audio Streaming**: Stream high-quality `.mp3` and `.flac` files directly from Google Drive without requiring local storage or permissions.
*   **Automated Metadata & Art**: Parse track information from standardized file names (e.g., `01 - Song Title`) and automatically display `cover.jpg` as the album artwork.
*   **Core Playback Controls**: Essential media playback functionality including play, pause, and track skipping with notification support.

## High-Level Tech Stack
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose
*   **Navigation**: Jetpack Navigation 3 (State-driven)
*   **Adaptive Strategy**: Compose Material Adaptive Library
*   **Concurrency**: Kotlin Coroutines
*   **Media Engine**: Media3 (ExoPlayer) for cloud-based audio streaming
*   **Cloud API**: Google Drive API for file traversal and media access

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
- **Acceptance Criteria:**
  - App correctly scans and displays music immediately after permission is granted
  - UI feedback is present

### Task_5_GDrive_Integration_And_Cleanup: Remove local storage support and permissions. Update README.md. Integrate Google Drive API to authenticate and scan the 'Vault/' directory structure.
- **Status:** COMPLETED
- **Updates:** Completed Task 5:
- **Acceptance Criteria:**
  - Local storage permissions and scanning logic removed from Manifest and code
  - README.md updated to reflect Google Drive only support
  - Google Drive API integration for authentication and 'Vault/' traversal is functional
  - API_KEY or OAuth configuration is integrated
  - Albums are correctly identified based on the folder hierarchy

### Task_6_Cloud_Streaming_Adaptive_UI_Verify: Implement Media3 streaming from Google Drive, update UI for adaptive layouts using Navigation 3, and perform final verification.
- **Status:** COMPLETED
- **Updates:** Completed Task 6 implementation:
- Implemented Media3 streaming from Google Drive using a custom DataSource.Factory with Auth headers.
- Updated UI to display album artwork (cover.jpg) and grouped library views.
- Polished adaptive UI and navigation transitions.
- Verified build stability.
- **Acceptance Criteria:**
  - Audio files (.mp3, .flac) stream directly from Google Drive
  - Album artwork (cover.jpg) and metadata are correctly displayed
  - UI uses Navigation 3 and Compose Material Adaptive Library for responsiveness
  - Build passes, app does not crash, and critic_agent verifies stability and requirements
- **Duration:** N/A

