# 改善下载队列稳定性与重试机制

改善下载队列的稳定性，取消冗余的逻辑，实现失败后自动无限重试。

## User Review Required

> [!IMPORTANT]
> 下载失败将不再显示“错误”状态，而是保持在“正在下载”或“等待中”状态并进行后台重试。这意味着用户可能不会立即看到明确的失败提示，但下载会持续尝试直到成功。

## Proposed Changes

### Database Layer (DAOs)

更新 DAO 以确保 `DownloadManager` 能够发现并恢复处于任何非完成状态（包括 `DOWNLOADING` 和 `ERROR`）的下载任务。

#### [MODIFY] [SongDao.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/database/SongDao.kt)
- 修改 `getSongsToDownloadFlow` 逻辑，包含 `ERROR` 状态。

#### [MODIFY] [AlbumDao.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/database/AlbumDao.kt)
- 修改 `getAlbumsToDownloadFlow` 逻辑，包含 `DOWNLOADING` 和 `ERROR` 状态。

---

### Core Logic (Download Manager)

核心下载逻辑重构，实现无限重试、指数退避（Exponential Backoff）以及更精简的流程。

#### [MODIFY] [DownloadManager.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/download/DownloadManager.kt)
- **无限重试**: 修改 `downloadWithRetry` 以支持无限循环重试，并增加退避延迟（最高 60 秒）。
- **流程优化**:
    - 移除 `handleDownloadError` 中将状态设置为 `ERROR` 的逻辑，避免任务进入死胡同。
    - 在观察器中包含 `DOWNLOADING` 和 `ERROR` 状态的任务，确保应用重启或遇到暂时错误后能自动恢复。
    - 移除观察器中一些可能导致冗余处理的过滤条件。

## Verification Plan

### Automated Tests
- 运行现有的 `DownloadProgressTest.kt` 确保基本逻辑未破坏。

### Manual Verification
1. **断网重试测试**: 在下载过程中关闭网络。
    - 预期：下载任务保持在“正在下载”状态，不报错。
    - 预期：重新连接网络后，下载自动恢复并成功完成。
2. **应用重启恢复测试**: 在有任务正在下载或失败时强杀并重启应用。
    - 预期：重启后，之前未完成的任务自动重新进入下载队列。
3. **并发稳定性测试**: 同时启动多个专辑下载（超过信号量限制 6 个）。
    - 预期：队列稳定运行，任务有序处理。
