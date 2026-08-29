# 架构深度优化方案

进一步提升多账户架构的稳定性与简洁性，实现全自动的会话管理。

## Proposed Changes

### [Core] 自动化会话管理

#### [MODIFY] [SessionManager.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/SessionManager.kt)
- 移除手动 `switchSession` 方法。
- 新增 `init(context, settingsRepository)`，内部通过协程观察 `googleAccountName`。
- 实现自动切换逻辑，确保账户变更时 `currentSession` 自动更新。

#### [MODIFY] [MainActivity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/MainActivity.kt)
- 移除 `LaunchedEffect` 中手动调用 `switchSession` 的代码。
- 仅保留 `playbackViewModel.stopPlayback()` 以确保切换时停止音频。

---

### [Data] Repository 与依赖优化

#### [MODIFY] [MusicRepository.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/repository/MusicRepository.kt)
- 引入 `withSession` 扩展辅助函数，消除重复的 `flatMapLatest` 和空检查。
- 简化同步逻辑。

#### [MODIFY] [Dependencies.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/Dependencies.kt)
- 整合初始化逻辑，确保 `SessionManager` 的自动观察机制在 App 启动时正确开启。

---

### [Stability] 错误处理与健壮性

#### [MODIFY] [SonarkDatabase.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/data/database/SonarkDatabase.kt)
- 在 `getDatabase` 中加入简单的内存缓存，防止短时间内重复创建相同账户的连接。

## Verification Plan

### Automated Tests
- 验证切换账户后，`MusicRepository.getSongsFlow()` 是否自动发射新数据。
- 模拟快速连续切换账户，检查是否会出现数据库已关闭（Database closed）异常。

### Manual Verification
- 在 `AccountPopDialog` 切换账户，观察 UI 是否流畅刷新。
- 确认下载队列在切换账户后立即指向新的存储路径。
