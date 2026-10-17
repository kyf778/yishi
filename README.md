# 忆时 Yishi

一个**本地优先的 Android 记忆备忘录与倒计时提醒 App**（数据库系统课程设计）。

拍下截图这一刻，到点它回来找你 —— AI 从截图中提取截止时间，自动建倒计时、排提醒，到点以通知栏横幅提醒你。

当前版本：**v0.12.0**（versionCode 13） · [下载 APK](#下载)

---

## 功能

### AI 截图识别
- 选一张或多张截图（聊天记录、通知、公告、课程表都行），可附一句备注
- 识别出截图中**所有**未来事项，列出来让你勾选要建哪些（都要 / 只要其中一个 / 都不要）
- 长截图自动**宽度优先切块**（每块 ≤1400px，重叠 120px），中文不糊，9000px 长图也能识别
- 识别后自动**打标签**（作业 / 考试 / 会议…）+ 关键词（奖学金 / 实验报告…）
- 识别历史落库（模型、提示词版本、置信度），详情页可回看「AI 当时认出了什么」

### 事项管理
- 手动新增、编辑（标题 / 备注 / 截止时间，改时间自动重排提醒）
- 完成、删除（都有二次确认）；**长按卡片**进入批量模式，可多条一起完成 / 删除
- 搜索（标题 + 备注）、排序（截止时间 / 创建时间 / 标题）、**标签筛选**
- 历史区：已完成的事项不消失，统计页随时回看，**可一键恢复**

### 提醒
- **自适应提前量**：剩 5 分钟 → 提前 1 分钟；剩 2 小时 → 提前 30、5 分钟（10 分钟的短事项也能提醒上）
- 优先采用 AI 建议的提醒时间，没有才用默认值
- **通知栏提醒**（WorkManager 调度，非闹钟）：
  - **所有提醒都弹横幅**（高优先级渠道 + 提示音 + 震动）
  - BigTextStyle 展开显示摘要
  - 三个操作按钮：**完成了 / 稍后 10 分钟 / 打开**
- 提醒记录落库：每次触发都记下「计划时间 / 实际触发时间 / 结果」，可追溯
- 设置页「提醒可靠性」自检：通知权限、电池优化白名单，一项不过就明示
- 开机、更新、时区变化后自动重排提醒

### 其它
- 深蓝渐变主题卡（考试=夜空月亮、作业=流星、逾期=红色警示）
- 统计页：状态分布、类型分布（中文显示）、历史区
- 全角日期兼容（`2026－12－12 09：00` 也能认）

## 下载

| 文件 | 说明 |
|---|---|
| [yishi-v0.12.0-release.apk](yishi-v0.12.0-release.apk) | 11.9 MB，已签名（release） |

Android 8.0+（minSdk 26）。下载后直接安装即可。

## AI 模型接入

默认**手机直连**腾讯 CodeBuddy（WorkBuddy 国内版）的积分模型，无需自建服务：

```text
端点：https://www.codebuddy.cn/v2/chat/completions
模型：deepseek-v4.1-flash（x0.11 倍率，支持图片，1M 上下文）
```

App 内置了 CodeBuddy 的协议适配（`CodeBuddyDirectProvider.kt`），满足其五条硬性要求：

1. 强制 `stream: true`（非流式 400/11101）
2. 必须带身份头（`X-Product-Code` / `X-Conversation-*` / `X-IDE-*`）
3. `messages[0]` 必须是 system（否则 400/11128）
4. 图片必须与文字在同一条 user 消息
5. `access_token` 过期可用 `refresh_token` 静默续期

设置页可换任意 OpenAI 兼容服务（填 Base URL / Key / 模型即覆盖内置）。

> ⚠️ 内置凭据是课程项目的 MVP 折衷：反编译 APK 可提取 token，但只能消耗该账号的 AI 积分。个人部署请在设置页填自己的 key。

### 实测教训（写在这里防止再踩）

| 结论 | 数据 |
|---|---|
| Tailscale Funnel 不适合做图片中转 | DERP 中继到旧金山（154ms），TLS 握手 3.3-7.5s；同一张图经 Funnel 88s，直连 3.7s |
| CodeBuddy 忽略所有「关思考」字段 | 8 种写法（reasoning.enabled / thinking.type / enable_thinking…）reasoning_tokens 全部不变 |
| `reasoning:{enabled:false}` 会**降低** deepseek-v4.1-flash 准确率 | 发它 1/3 对，不发 3/3 对（同图各采样 3 次） |
| 缩图不能提速 | 视觉 token 不与像素成正比：1440 宽 2798 tok vs 900 宽 2802 tok |

## 数据保存

- **纯本地**：所有数据（Room 数据库）只存在手机里，没有任何服务器
- **覆盖更新不丢数据**：Android 覆盖安装保留应用数据；卸载或「清除数据」才会清空
- 每次识别的来源截图副本、AI 解析记录都在本地可查

## 工程结构

```text
app/src/main/java/com/kyf778/yishi/
├── MainActivity.kt                 全部界面（Compose）：首页/统计/提醒记录/识别/编辑/批量
├── ai/
│   ├── AiProvider.kt               接口 + 标准 OpenAI 兼容实现（含重试）
│   ├── CodeBuddyDirectProvider.kt  手机直连 CodeBuddy（SSE 协议 + 身份头 + 多事项）
│   ├── AiModels.kt                 AiInput / ExtractedMemory 数据模型
│   └── DeadlineParser.kt           宽容截止时间解析（8 种格式 + 全角归一化）
├── data/
│   ├── Entities.kt                 8 张 Room 表（memory/source/event/reminder/tag/…）
│   ├── Daos.kt                     查询（含标签筛选、导出恢复）
│   ├── Repository.kt               业务逻辑（自适应提醒、类型归一、批量操作）
│   └── BackupManager.kt            备份导出/恢复（可选）
├── reminder/
│   ├── ReminderScheduler.kt        WorkManager 调度（非闹钟）
│   ├── ReminderWorker.kt           到点发通知栏提醒
│   ├── ReminderReceiver.kt         通知按钮处理（完成/稍后）
│   └── BootReceiver.kt             开机/更新/时区变化后重排
└── settings/AppSettings.kt         DataStore + Keystore 配置
```

## 构建

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 正式包（已签名）
```

环境：JDK 17、AGP 8.7.3、Kotlin 2.0.21、Gradle 8.9、compileSdk 35、minSdk 26。

> 国内网络注意：Gradle/Java 不读 Windows 系统代理，直连 GitHub CDN 只有 8 KB/s。
> `gradle.properties` 已配置 Clash 代理（7897），换机器需同步修改。

## 测试

| 套件 | 结果 |
|---|---|
| 视觉识别回归（10 场景：无日期/明确日期/相对日期/英文/表格/长图/超长图…） | 10/10 |
| 日期解析 | 17/17 |
| 全角日期 | 8/8 |
| 数据层自检（标签/AI记录/提醒建议 落库读回） | 全部通过 |
| 端到端（真机：识别→建卡→到点通知） | 通过 |

## 隐私

- 事项、截图副本、识别记录只保存在手机本地
- 识别时截图会发送到 AI 服务（默认 CodeBuddy，可换）
- 不要发送敏感信息（身份证、密码等）——截图内容会离开手机

## 限制

- 识别依赖在线模型，离线不可用
- 语音输入未包含（小米 HyperOS 限制第三方调用系统语音识别；可用输入法键盘的麦克风替代）
- 课程设计 MVP：无账号、无云同步、无多端
