# Sonark 自动化测试指南

为了确保多账户管理与存储隔离功能的稳定性，本项目建立了一套完善的自动化测试体系。本指南介绍如何安全地运行这些测试，并确保敏感信息（如 Token）不被泄露。

## 测试架构

本项目采用 **凭证注入 (Credential Injection)** 机制。测试 Token 不存储在项目代码中，而是通过测试指令动态传入。

### 1. 逻辑压力测试 (Session Stress Test)
- **目标**：验证极速切换账户时系统的稳定性（防止 SQLite closed 等崩溃）。
- **文件**：`app/src/androidTest/java/md/oak/sonark/ui/SessionSwitchStressTest.kt`

### 2. 真实同步集成测试 (Drive Sync Test)
- **目标**：验证在提供真实 Token 的情况下，Google Drive 库同步流程的完整性。
- **文件**：`app/src/androidTest/java/md/oak/sonark/data/DriveSyncIntegrationTest.kt`

---

## 如何运行测试 (安全模式)

为了保护隐私，严禁将 Token 写入项目文件。请按照以下步骤操作：

### 第一步：获取测试 Token
1. 在 App 运行状态下，点击头像打开账户弹出列表。
2. 点击对应账户右侧的 **钥匙图标 (VpnKey)**。
3. 在 Logcat 中搜索 `SonarkTest` 标签，复制 Token。

### 第二步：使用命令行运行 (推荐)
打开终端 (Terminal)，执行以下命令。针对 Windows PowerShell，建议为每个参数添加双引号以防止解析错误：

```powershell
./gradlew connectedCheck `
  "-Ptest.account_a_email=USER_A_EMAIL" `
  "-Ptest.account_a_token=USER_A_TOKEN" `
  "-Ptest.account_b_email=USER_B_EMAIL" `
  "-Ptest.account_b_token=USER_B_TOKEN"
```

> **注意**：
> 1. 本项目已在 `build.gradle.kts` 中配置了自动转发，参数名必须以 `-Ptest.` 开头。
> 2. Token 具有时效性，如果测试由于 401 错误失败，请重新获取最新 Token。

---

## 安全注意事项

1. **零文件残留**：本项目已在 `.gitignore` 中排除所有敏感路径，且测试代码不再读取 JSON 配置文件。
2. **AI 隔离**：由于不使用物理文件存储 Token，IDE 内置的 AI Agent 无法索引到你的私密授权信息。
3. **Logcat 清理**：测试或调试完成后，建议执行 `adb logcat -c` 清除缓冲区中的 Token 记录。

## 故障排除

- **测试静默退出**：请检查命令行参数中的 Key 名是否拼写正确（必须包含 `account_a_email` 等前缀）。
- **数据库错误**：如果测试由于 `IllegalStateException` 崩溃，通常意味着 Session 切换的延迟关闭逻辑触发了竞态条件，请及时向开发团队反馈错误堆栈。
