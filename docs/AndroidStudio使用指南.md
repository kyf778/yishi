# 用 Android Studio 继续开发「忆时」

> 环境已经全部配好，直接照做即可。

---

## 一、当前状态（已验证）

| 项目 | 状态 |
|---|---|
| Android Studio | ✅ 已装（Rabbit 1 / 2026.2.1），**界面已是中文** |
| 项目同步 | ✅ **成功**（`onSuccess`，用时 2 分 07 秒） |
| Gradle | ✅ 8.9，wrapper 缓存完整（离线可构建） |
| JDK | ✅ 锁定 JDK 17（`gradle.properties` 里写死） |
| 手机连接 | ✅ Redmi 23113RKC6C 已识别，Studio 设备下拉框里能看到 |
| 网络代理 | ✅ 已配（解决同步卡死的根因） |

---

## 二、日常操作：改代码 → 装到手机

### 方式 A：点运行按钮（最简单）

1. 顶部工具栏确认设备是 **Xiaomi 23113RKC6C**
2. 点绿色 **▶ 运行**（或按 `Shift + F10`）
3. 等编译完成，App 自动装到手机并启动

> 因为 debug 和 release 现在用**同一个签名**，不会再要求你先卸载重装。

### 方式 B：命令行（想快一点）

在 Studio 底部的 **Terminal** 里：

```bash
./gradlew assembleDebug          # 编译
adb install -r app/build/outputs/apk/debug/app-debug.apk   # 装到手机
```

或者装正式的签名包（体积只有 12MB）：

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 三、调试：在电脑上看手机 + 看日志

### 1. 设备镜像（在电脑上操作手机）

**视图 → 工具窗口 → 运行中的设备**（View → Tool Windows → Running Devices）

- 面板里选中你的 Redmi → 点镜像按钮
- 手机上会弹「允许通过 USB 进行屏幕投射和输入控制」→ 点允许
- 之后就能用鼠标点手机界面了

> ⚠️ 如果能看到画面但**鼠标点不动**，去手机：
> **设置 → 更多设置 → 开发者选项 → 打开「USB 调试（安全设置）」**
> 小米/HyperOS 必须开这个，否则输入注入被拦。

### 2. Logcat（看日志和崩溃）

**视图 → 工具窗口 → Logcat**

在搜索框输入过滤条件：

```
package:com.kyf778.yishi
```

只看崩溃：

```
package:com.kyf778.yishi level:error
```

### 3. 布局检查器（调 UI 用）

**工具 → 布局检查器**（Tools → Layout Inspector），能看到界面层级和各控件属性。

---

## 四、我做了什么修改（你可以直接改）

主要代码位置：

```text
app/src/main/java/com/kyf778/yishi/
├── MainActivity.kt          界面 + 一键截图识别（runImport 是核心函数）
├── ai/
│   ├── AiProvider.kt        AI 请求 + 提示词（system prompt 在这里）
│   ├── DeadlineParser.kt    日期解析（要加格式改这里）
│   └── AiModels.kt          数据模型
├── data/                    Room 数据库
├── reminder/                提醒调度
└── settings/AppSettings.kt  AI 配置默认值（AiDefaults）
```

### 想换视觉模型？

改 `settings/AppSettings.kt`：

```kotlin
object AiDefaults {
    const val VISION_BASE_URL = "https://api.kilo.ai/api/gateway"
    const val VISION_MODEL = "nvidia/nemotron-3.5-super-vl:free"
    const val VISION_API_KEY = ""
}
```

或者不改代码——App 里点**设置**图标，填 Base URL / API Key / 模型 ID，**填了就优先用你的**。

### 想改识别提示词？

`ai/AiProvider.kt` 里的 `val system = """..."""`。

---

## 五、⚠️ 换电脑 / 换网络时注意

### 代理配置（重要）

Gradle/Java **不使用 Windows 系统代理**，直连 GitHub 会被限速到约 8 KB/s，
同步会卡死几十分钟。`gradle.properties` 里已配好：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7897
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7897
```

**如果你的 Clash 端口不是 7897**，改这里。没开 Clash 的话，同步会变慢但最终能完成。

### 签名文件

`yishi-release.jks`（密码 `yishi2026`，别名 `yishi`）——**别丢**，
丢了就没法覆盖安装升级（必须先卸载，会清掉数据）。

---

## 六、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 同步一直转圈 | 代理没配 / Clash 没开 | 检查 `gradle.properties` 代理段 |
| 装包报签名冲突 | 用了不同 keystore | 现在已统一，重装一次即可 |
| 镜像窗口点不动手机 | 没开「USB 调试（安全设置）」 | 手机上打开该开关 |
| 识别报「没找到截止时间」 | 图里确实没日期，或图太长 | 换张带明确日期的截图 |
| `adb` 找不到设备 | 数据线松了 / 未授权 | `adb devices` 确认，手机上点允许 |
