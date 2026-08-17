# Implementation Plan - Unified Music Library and Playback UI

Develop a unified music library and playback UI using Compose Material Adaptive, with enhanced features like search, sorting, shuffle, repeat, and queue management.

## Proposed Changes

### Build Configuration
#### [MODIFY] [build.gradle.kts](file:///C:/Users/airi/wksp/Sonark/app/build.gradle.kts)
- Add `androidx.compose.material3:material3-adaptive-navigation-suite` dependency.

### Data Layer
#### [MODIFY] [MainViewModel.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/MainViewModel.kt)
- Add search query and sorting logic.
- Expose filtered and sorted songs.

### Playback Logic
#### [MODIFY] [PlaybackViewModel.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/PlaybackViewModel.kt)
- Add `shuffleEnabled`, `repeatMode`, and `queue` state.
- Implement methods to toggle shuffle/repeat and play a queue.
- Sync with `MediaController` state.

### UI Components
#### [MODIFY] [LibraryScreen.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/LibraryScreen.kt)
- Implement `SearchBar` and sorting options.
- Optimize for adaptive layouts (multi-column grid on larger screens).

#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/screens/PlayerScreen.kt)
- Add Shuffle and Repeat buttons.
- Add a Queue view (expandable or bottom sheet).
- Polish M3 styling.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/MainActivity.kt)
- Use `NavigationSuiteScaffold` for adaptive navigation.
- Implement `ListDetailPaneScaffold` for the Library if appropriate.

## Verification Plan
### Automated Tests
- N/A (Manual verification prioritized for UI/Adaptive changes)

### Manual Verification
- Verify `NavigationSuiteScaffold` switches between Bottom Bar (phone) and Navigation Rail (tablet).
- Verify Library search filters songs correctly.
- Verify Library sorting (Title, Artist) works.
- Verify Shuffle and Repeat modes toggle correctly and affect playback.
- Verify Queue view displays upcoming songs.
- Check layout stability during orientation changes/resize.
