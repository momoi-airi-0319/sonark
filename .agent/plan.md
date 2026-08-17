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

### Task_1_Infrastructure_Data: Configure project dependencies (Media3, Navigation 3, Adaptive, Google Drive API), set up Navigation 3 architecture, and implement local storage and Google Drive music retrieval.
- **Status:** COMPLETED
- **Updates:** Refinement complete:
- **Acceptance Criteria:**
  - Project builds successfully
  - Local storage scanning retrieves music files
  - Google Drive API integration is functional with API_KEY/Auth
  - Navigation 3 skeleton is implemented

### Task_2_Playback_Engine: Implement Jetpack Media3 MediaSessionService and ExoPlayer to handle background audio playback, seek bars, and media notifications.
- **Status:** COMPLETED
- **Updates:** Completed Task 2:
- **Acceptance Criteria:**
  - Audio playback works for local and remote files
  - Media notification shows controls and metadata
  - Playback state is synced with the UI

### Task_3_Adaptive_UI: Develop the unified music library and playback UI using Compose Material Adaptive for optimized layouts across phones and tablets.
- **Status:** COMPLETED
- **Updates:** Completed Task 3:
- **Acceptance Criteria:**
  - UI is adaptive across different screen sizes
  - Library browsing and search functionality work
  - Playback screen includes queue management, shuffle, and repeat
  - App does not crash

### Task_4_Run_Verify: Final Run and Verify: Ensure application stability, confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Final verification complete:
- Adaptive UI logic confirmed (Navigation Rail, Grid).
- Google Drive sign-in flow functional.
- Settings screen polished and fully implemented.
- No crashes or placeholders.
The app meets all project brief requirements.
- **Acceptance Criteria:**
  - App does not crash during standard usage
  - Build pass
  - All features functional as per project brief
  - Make sure all existing tests pass
- **Duration:** N/A

