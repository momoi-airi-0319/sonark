# 播放器跳转优化计划

实现点击系统通知栏播放卡片后直接跳转到播放器界面。

## 用户审核要求
> [!IMPORTANT]
> 需要修改 `PlaybackService` 以发送特定 Action 的 Intent，并在 `MainActivity` 中捕获并处理该 Intent。

## 提出的修改

### Playback 模块

#### [MODIFY] [PlaybackService.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/playback/PlaybackService.kt)
- 添加 `ACTION_SHOW_PLAYER` 常量。
- 修改 `createMediaSession` 以在 `PendingIntent` 中使用该 Action。

### UI 模块

#### [MODIFY] [MainActivity.kt](file:///C:/Users/airi/wksp/Sonark/app/src/main/java/md/oak/sonark/MainActivity.kt)
- 添加处理 Intent 的逻辑。
- 在 Compose 中监听 Intent 触发，并使用 `Navigator` 跳转到 `PlayerKey`。

## 验证计划

### 手动验证
- 启动应用并播放一首歌。
- 退出应用回到主屏幕（保持后台播放）。
- 点击通知栏中的播放卡片。
- 验证应用是否重新打开并直接跳转到播放器界面（PlayerScreen）。
