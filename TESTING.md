# Sonark SDK 测试指南

本文档介绍如何运行 Sonark Rust SDK 的测试。

## 1. 运行环境

测试主要在 `rust-sdk` 目录下执行。
```bash
cd rust-sdk
```

## 2. 单元测试 (Unit Tests)

单元测试覆盖了核心逻辑、数据库操作和 CUE 解析。

- **运行全部单元测试**: `cargo test --package sonark-sdk --lib`
- **运行特定模块**:
  - `cue::tests`: CUE 解析器测试
  - `db::tests`: 数据库 CRUD 测试
  - `tests`: 库函数（文件名/文件夹解析）测试

## 3. Google Drive 集成测试 (Real API)

`SonarkEngine` 包含基于真实 Google Drive 账户的集成测试。

### 运行步骤：
1. **获取 Token**: 从 [Google OAuth Playground](https://developers.google.com/oauthplayground/) 获取具有 `drive.readonly` 权限的 Access Token。
2. **设置变量**: `$env:SONARK_TEST_TOKEN="你的Token"` (Windows) 或 `export SONARK_TEST_TOKEN="..."` (Unix)。
3. **执行全链路测试**:
   ```bash
   cargo test --package sonark-sdk --lib engine_integration_tests::test_full_workflow_sync_download_scan -- --ignored --nocapture
   ```

### 标准测试账号结构要求
为了通过集成测试，建议在 Google Drive 的 `Vault` 文件夹下建立以下结构：
```text
Vault/
├── Artist A - Album One/
│   ├── 01 Track A.mp3         (带 ID3 标签)
│   ├── 02 Track B.flac        (不带标签，测试解析文件名)
│   └── cover.jpg              (测试封面识别)
└── Artist B - Album Two/      (测试 CUE 场景)
    ├── cd.flac
    └── cd.cue                 (FILE "cd.flac" WAVE，包含至少 2 个 TRACK)
```

## 4. 模拟网络测试 (Mock Tests)

如果你没有 Token 或处于离线状态，可以运行 Mock 测试，它通过模拟 Google API 响应来验证逻辑：
```bash
cargo test --package sonark-sdk --lib mock_tests
```

## 5. 调试建议

如果测试失败，使用 `--nocapture` 查看详细日志。
```bash
cargo test --package sonark-sdk -- --nocapture
```
