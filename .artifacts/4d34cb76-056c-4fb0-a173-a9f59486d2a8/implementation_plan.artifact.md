# Fix Progress Bar and Duration Issues

The user reported that the progress bar sometimes shows the wrong total time, jumps to incorrect positions when clicked, and moves at an inconsistent speed during dragging. These issues are caused by:
1. Incorrect duration calculation for CUE file tracks (initialized to 0).
2. Delayed duration updates in `PlaybackViewModel` during song transitions.
3. Missing `endPositionMs` in Media3 `ClippingConfiguration` for CUE tracks.
4. Lack of local state handling in `WavySlider` during dragging, causing "fighting" between UI updates and user input.

## Proposed Changes

### Data Layer

#### [MODIFY] [DriveMusicProvider.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/provider/DriveMusicProvider.kt)
- Update `parseCueAlbum` to calculate track durations based on the next track's start offset.
- This ensures CUE tracks have a valid duration from the start.

### Playback Logic

#### [MODIFY] [PlaybackViewModel.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/PlaybackViewModel.kt)
- In `onMediaItemTransition`, immediately update `_duration` and `_playbackProgress` using the metadata from the new song.
- In `playQueue`, set `setEndPositionMs` in `ClippingConfiguration` for CUE tracks (`startOffset + song.duration`).

### UI Components

#### [MODIFY] [WavySlider.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/ui/components/WavySlider.kt)
- Introduce a local `draggingValue` state to handle user interaction smoothly.
- Ensure the slider thumb reflects the user's drag position immediately, ignoring external `value` updates until the drag ends.
- Properly consume pointer events during drag.

## Verification Plan

### Automated Tests
- Unit tests for `parseCueTime` and duration calculation in `DriveMusicProvider` (if applicable).
- Verification of `PlaybackViewModel` state transitions during song switches.

### Manual Verification
- Play a CUE album and verify that each track has the correct duration.
- Rapidly switch songs and verify the progress bar doesn't jump or show 0:00.
- Drag the progress bar and verify it follows the finger smoothly without jitter.
- Click various positions on the progress bar and verify it seeks to the correct time.
