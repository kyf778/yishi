# GitHub 申诉材料（可直接复制粘贴）

> 用法：打开 https://support.github.com/contact/cannot_sign_in
> （如果你能登录，用 https://support.github.com/contact/reinstatement）
> 验证邮箱后，把下面的英文/中文内容填进申诉表单。

---

## 一、申诉表单（英文版，推荐）

### Subject / Title

```
Account Suspension Appeal - Individual Student Developer (kyf778)
```

### Description / Body

```
Hello GitHub Support,

My account "kyf778" (email: 694982318@qq.com) has been suspended. I would like to
appeal this decision and request reinstatement.

## About me

I am a university student in China, currently taking a Database Systems course.
I created this GitHub account to host my course project — an Android app called
"忆时 (Yishi)", a local-first memo and countdown reminder application. The app is
a personal learning project, not a commercial product.

## My activity on this account

- Created around 2026-10-16
- Pushed 4 commits over 2 days
- Published 1 release (v0.11.0) with the APK
- The repository is small: only 104 tracked files, largest file 0.18 MB

I have not engaged in any spam, automated activity, mass following, or creation
of multiple accounts. I use this account only for my own coursework.

## Possible cause I identified myself

Reviewing my repository, I found that I had accidentally committed hard-coded
authentication tokens (JWT strings for a third-party AI service) in the file
`app/src/main/java/com/kyf778/yishi/settings/AppSettings.kt`.

These are credentials for a public AI service (CodeBuddy) that I use in my app.
I now understand this violates the practice of not committing secrets to
repositories, and I believe GitHub's secret scanning may have flagged this.

I did not intend to expose anyone's credentials, and these tokens belong to my
own account on that service.

## What I will do immediately

1. Remove the hard-coded tokens from the source code
2. Load credentials from local configuration that is excluded from git
3. Rewrite the git history to purge the tokens from all past commits
4. Add proper .gitignore entries for secret files

I have also already made my repository private-ready and will review all content
before making it public again.

## Request

Please review my case and consider reinstating my account. I am willing to make
any changes necessary to comply with GitHub's Terms of Service and Acceptable
Use Policies.

If there is any specific content or behaviour you need me to address, please
let me know and I will fix it immediately.

Thank you for your time.

Best regards,
kyf778
```

---

## 二、申诉表单（中文版，备用）

### 标题

```
账号封禁申诉 - 学生个人学习项目（kyf778）
```

### 正文

```
GitHub 支持团队您好，

我的账号 "kyf778"（邮箱：694982318@qq.com）被封禁，我希望申诉并恢复账号。

## 关于我

我是中国的一名大学生，正在学习《数据库系统》课程。我创建这个 GitHub 账号
是为了托管我的课程设计——一个名为「忆时（Yishi）」的 Android 应用，是一个
本地优先的备忘 + 倒计时提醒工具。这是个人学习项目，不是商业产品。

## 我的账号活动

- 账号创建于 2026-10-16 左右
- 两天内推送了 4 次提交
- 发布了 1 个版本（v0.11.0）和 APK 文件
- 仓库很小：仅 104 个文件，最大文件 0.18 MB

我没有进行任何垃圾信息、自动化滥用、批量关注或注册多账号的行为。
我只用这个账号做自己的课程作业。

## 我自己排查到的可能原因

检查仓库后我发现，我在文件
`app/src/main/java/com/kyf778/yishi/settings/AppSettings.kt` 中
不小心把第三方 AI 服务的认证 token（JWT 字符串）硬编码后提交了。

这些是我自己的 CodeBuddy 账号的凭据，用于我在 App 中调用 AI 服务。
我现在明白这种做法违反了「不应把密钥提交到仓库」的规范，
我怀疑 GitHub 的密钥扫描功能因此标记了我的仓库。

我没有意图泄露任何人的凭据，这些 token 都属于我自己。

## 我将会立即做的事

1. 从源代码中移除硬编码的 token
2. 改为从本地配置文件读取（该文件已被 git 忽略）
3. 重写 git 历史，彻底清除 token
4. 完善 .gitignore，排除密钥文件

## 请求

请审查我的情况，考虑恢复我的账号。我愿意做任何必要的修改，
以符合 GitHub 的服务条款和可接受使用政策。

如果有任何具体内容或行为需要我处理，请告诉我，我会立即改正。

感谢！
kyf778
```

---

## 三、提交后的注意事项

| 事项 | 说明 |
|---|---|
| **不要重复提交** | 会拖慢处理，可能被视为滥用 |
| 等待时间 | 通常 1-7 个工作日 |
| 查邮箱 | 包括垃圾邮件文件夹 |
| 保持账号可联系 | 不要改邮箱 |

---

## 四、同步要做的技术修复（不管申诉结果）

即使账号没恢复，这个安全问题也必须修：

### 1. 移除硬编码 token

**当前代码**（`AppSettings.kt` L58-59）：
```kotlin
const val CODEBUDDY_ACCESS_TOKEN = "<YOUR_JWT_TOKEN>"
const val CODEBUDDY_REFRESH_TOKEN = "<YOUR_JWT_TOKEN>"
```

**改为**：
```kotlin
// 值从 local.properties 读（该文件已在 .gitignore 里）
const val CODEBUDDY_ACCESS_TOKEN = BuildConfig.CODEBUDDY_TOKEN
const val CODEBUDDY_REFRESH_TOKEN = BuildConfig.CODEBUDDY_REFRESH
```

**app/build.gradle.kts**：
```kotlin
android {
    defaultConfig {
        // 从 local.properties 读取，不提交到 git
        val props = java.util.Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "CODEBUDDY_TOKEN",
            "\"${props.getProperty("codebuddy.token") ?: ""}\"")
        buildConfigField("String", "CODEBUDDY_REFRESH",
            "\"${props.getProperty("codebuddy.refresh") ?: ""}\"")
    }
    buildFeatures { buildConfig = true }
}
```

**local.properties**（不提交）：
```properties
codebuddy.token=<YOUR_JWT_TOKEN>
codebuddy.refresh=<YOUR_JWT_TOKEN>
```

### 2. 完善 .gitignore

```gitignore
# 密钥/凭据
local.properties
*.jks
*.keystore
*.pem
*.key
secrets.properties

# 构建产物
app/build/
build/
*.apk
*.aab
```

### 3. 重写历史（申诉成功后做）

```bash
# 安装 git-filter-repo
pip install git-filter-repo

# 从所有历史中删除 AppSettings.kt 里的 token
# （或者替换成占位符）
git filter-repo --path app/src/main/java/com/kyf778/yishi/settings/AppSettings.kt --invert-paths
```

**注意**：重写历史会改变所有 commit hash，需要在申诉成功、账号恢复后做。

---

*材料生成于 2026-10-18 17:48*
