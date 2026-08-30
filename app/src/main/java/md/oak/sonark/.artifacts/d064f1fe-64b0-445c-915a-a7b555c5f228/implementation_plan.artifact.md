# Fix Download Queue Logic

The current download queue logic has several issues:
1.  **Lack of Priority**: All pending tasks are started simultaneously and wait in a semaphore, with no regard for their remaining progress.
2.  **System Interference**: The system resets download statuses on startup, which might conflict with user intent.
3.  **Concurrency Management**: Too many coroutines are launched for large queues.

## Proposed Changes

### [DownloadManager](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/download/DownloadManager.kt)

#### [MODIFY] [DownloadManager.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/download/DownloadManager.kt)
- **Remove automatic status reset**: Remove `resetAllDownloadingStatus()` from the `start()` method to prevent system interference with task states.
- **Implement Priority Scheduling**:
    - Merge song and album cover download flows using `combine`.
    - Filter tasks that are in `PENDING`, `ERROR`, or `DOWNLOADING` status.
    - Sort tasks by `downloadProgress` in descending order (tasks closer to completion get higher priority).
    - Limit the active jobs to the top 6 tasks.
- **Dynamic Job Management**:
    - Automatically cancel active jobs for tasks that drop out of the top 6 priority list.
    - Start new jobs only for tasks that enter the top 6.
- **Refine Download Logic**:
    - In `downloadSong` and `downloadAlbumCover`, allow `DOWNLOADING` status to pass the initial check (enabling resumed downloads).
    - Maintain existing progress instead of resetting to 0 when starting a download job.
    - Remove the redundant `downloadSemaphore` as the job management logic will strictly enforce the limit of 6.

## Verification Plan

### Automated Tests
- Run existing `DownloadProgressTest.kt`.
- Create a new unit test for `DownloadManager` to verify:
    - Only top 6 tasks by progress are active.
    - A higher-progress task preempts a lower-progress one.
    - Paused tasks are never picked up.

### Manual Verification
1.  Add many tracks to the download queue.
2.  Observe that only 6 are downloading at once.
3.  Pause some tracks and observe they stop.
4.  Resume a track that is nearly finished (high progress) and observe it jumps to the front of the queue (preempting others if necessary).
5.  Restart the app and verify that downloads resume correctly from where they left off without resetting progress.
