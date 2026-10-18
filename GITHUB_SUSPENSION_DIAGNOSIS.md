# GitHub 账号封禁诊断报告

> 账号：kyf778
> 诊断时间：2026-10-18 17:45
> 状态：**suspended（暂停）**，所有 API 返回 403，公开页面返回 404

---

## 一、客观事实（已实测）

| 检查项 | 结果 | 说明 |
|---|---|---|
| `GET /user`（带 token） | **403 "Your account was suspended"** | token 本身有效，是账号被停 |
| `github.com/kyf778`（匿名） | **404** | 账号整体隐藏 |
| `github.com/kyf778/yishi`（匿名） | **404** | 仓库一并隐藏 |
| SSH 推送 | publickey denied | 密钥未注册（独立问题） |
| 所有 API endpoint | 均返回 suspended | 连 `/rate_limit` 都拒绝 |
| 推送时间线 | v0.11.0 发布成功（10-18 00:51）→ v0.12.0 推送失败 | **封禁发生在 00:51 之后** |

---

## 二、排查过的可能性

### ❌ 已排除

| 假设 | 证据 | 结论 |
|---|---|---|
| 仓库太大 | `.git` 仅 **0.8 MB**，跟踪 104 个文件，最大 0.18 MB | 排除 |
| 误提交构建产物 | `git ls-files` 无 `app/build/`、无 `.apk` | 排除 |
| 上传大文件 | 无 LFS、无大文件 | 排除 |
| 密钥文件泄露 | 无 `.env`/`.pem`/`.key` | 排除 |
| API 限流 | 403 消息明确是 suspended 而非 rate limit | 排除 |
| 网络/代理问题 | 换 endpoint 结果一致 | 排除 |

### ⚠️ 高度可疑（最可能的原因）

#### 1. 仓库含硬编码的第三方 JWT 凭据（最可能）

**位置**：`app/src/main/java/com/kyf778/yishi/settings/AppSettings.kt` 第 58-59 行

```kotlin
const val CODEBUDDY_ACCESS_TOKEN = "<YOUR_JWT_TOKEN>"  // JWT
const val CODEBUDDY_REFRESH_TOKEN = "<YOUR_JWT_TOKEN>" // JWT
```

**关键事实**：这两个 token 在**已推送的提交 `f7c2a6d`（v0.11.0）里**。

**为什么这会导致封禁**：
- GitHub 有 **Secret Scanning** 自动扫描已推送的仓库
- 检测到硬编码凭据（尤其是 JWT、API token）会触发安全告警
- 大规模或可疑的凭据泄露可能被标记为「账号被入侵 / 传播泄露凭据」
- 这类仓库有时会被自动暂停，等人工审核

#### 2. 账号较新 + 短时间大量操作

- 账号活跃时间：2026-10-16 首次提交 → 10-18 封禁（**仅 2 天**）
- 期间：4 次推送、1 次 Release + APK 上传、多次大文件上传尝试
- GitHub 反滥用系统对「新账号 + 高频操作」较敏感

#### 3. 网络环境

- 使用代理访问（`127.0.0.1:7897` Clash）
- 如果出口 IP 是共享的 VPN/数据中心 IP，可能与其他违规账号同 IP 被关联

---

## 三、申诉材料（已备好）

### 官方入口

**主要入口**（能登录时）：
```
https://support.github.com/contact/reinstatement
```

**无法登录时**（你的情况）：
```
https://support.github.com/contact/cannot_sign_in
```
验证邮箱后即可访问申诉表单。

### 申诉要点

1. **说明账号用途**：个人学习项目（Android 课程设计）
2. **主动承认并移除违规内容**：硬编码的第三方 token 会立即删除
3. **强调无恶意**：没有 spam、没有自动化滥用、没有多账号
4. **提供仓库说明**：忆时（Yishi）是一个本地备忘 + 倒计时提醒 App

---

## 四、无论申诉结果如何，这些都应该立即做

### 必修：移除硬编码凭据

```kotlin
// 现在（危险）
const val CODEBUDDY_ACCESS_TOKEN = "eyJhbGci..."

// 应该（安全）
// 从 BuildConfig 读，值放在 local.properties（不进 git）
const val CODEBUDDY_ACCESS_TOKEN = BuildConfig.CODEBUDDY_TOKEN
```

`.gitignore` 加：
```
local.properties
*.jks
*.keystore
```

### 建议：重写 git 历史

如果申诉成功，需要用 `git filter-repo` 把 token 从历史里彻底抹掉，否则旧提交仍然可见。

---

## 五、注意事项

| 事项 | 说明 |
|---|---|
| 申诉时效 | 决定后 **6 个月内**可申诉 |
| 审核方式 | **人工审核**，不是自动化 |
| 回复时间 | 通常 1-7 个工作日 |
| 可能结果 | 恢复 / 部分恢复 / 维持封禁 |
| 不要重复提交 | 重复申诉会拖慢处理 |

---

## 六、备用方案

如果申诉失败：

1. **注册新账号**，注意：
   - 用不同的邮箱
   - 换一个网络环境（避免同 IP 关联）
   - 不要在同一台机器短时间大量操作
2. **改推送到其他平台**：
   - Gitee（国内，快）
   - GitLab
   - Codeberg
3. **本地保留**：代码和 APK 都已在本地，不会丢

---

*报告生成于 2026-10-18 17:45*
