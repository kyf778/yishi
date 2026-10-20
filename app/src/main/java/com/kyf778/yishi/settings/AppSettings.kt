package com.kyf778.yishi.settings

import com.kyf778.yishi.BuildConfig

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsDataStore by preferencesDataStore("yishi_settings")

/** 出厂默认 AI 配置：装 App 即可用，无需手动填写。 */
object AiDefaults {
    // NAS 网关（公网 HTTPS，任何网络可达，手机不用装 Tailscale）
    // 网关后面挂着 free-llm-bridge，其中 codebuddy 车道由海外/国内的 CodeBuddy 账号提供
    const val BASE_URL = "https://nas-test.tailacf3df.ts.net/v1"
    // 密钥从 local.properties 注入 BuildConfig，绝不写进源码。
    // 见 AGENTS.md 铁律三：任何密钥都不准写进源码，推送前必须扫一遍。
    val API_KEY: String = BuildConfig.NAS_GATEWAY_KEY

    // 点名模型，**不要用 free-queue**：
    // 实测 free-queue 是「按容量自动挑车道」的哨兵，会挑到已经不通的 kilo 车道并 404。
    // 点名则稳定走 codebuddy 车道。
    //
    // 为什么选 deepseek-v4.1-flash 而不是免费的 hy3（同一张图实测）：
    //   hy3               — x0.00 免费，但会把"下午78节"编造成 14:00，且思考吃满输出预算
    //   deepseek-v4.1-flash — x0.11，返回 2026-10-20 不带编造钟点，最干净
    //   minimax-m3        — x0.25，抓了已过期的日期，不可用
    // 一次识别约 0.1-0.3 积分，积分足够用。
    const val MODEL = "deepseek-v4.1-flash"

    // 视觉识别同样走 NAS 网关（CodeBuddy 的模型都能读图）
    // 旧配置（Kilo 免费视觉）留作参考：https://api.kilo.ai/api/gateway
    const val VISION_BASE_URL = BASE_URL
    const val VISION_MODEL = MODEL
    val VISION_API_KEY: String = API_KEY

    // 兜底：Kilo 免费视觉模型（NAS 网关不通时可用，无需 key）
    const val FALLBACK_VISION_BASE_URL = "https://api.kilo.ai/api/gateway"
    const val FALLBACK_VISION_MODEL = "nvidia/nemotron-3.5-super-vl:free"
    val FALLBACK_VISION_API_KEY: String = ""

    // ── 手机直连 CodeBuddy 的凭据 ────────────────────────────────────
    // 为什么不走 NAS：经 Tailscale Funnel 时 TLS 握手 3.3-7.5s、首字节 7-17s
    // （家庭出口被中继到美国旧金山），同一张图 88 秒；直连 3.7-4.1 秒。
    //
    // ⚠️ 安全提示：这些值**不再硬编码在源码里**。
    //    之前直接写在代码里并提交到了 git，属于凭据泄露（GitHub Secret Scanning
    //    会标记这类仓库）。现在改为从 local.properties 读取，该文件在 .gitignore 里。
    //
    //    构建时由 app/build.gradle.kts 注入到 BuildConfig；
    //    如果 local.properties 里没配，这些值是空串，App 会走设置页里用户自己填的 Key。
    val CODEBUDDY_ACCESS_TOKEN: String = BuildConfig.CODEBUDDY_ACCESS_TOKEN
    val CODEBUDDY_REFRESH_TOKEN: String = BuildConfig.CODEBUDDY_REFRESH_TOKEN

}

class AppSettings(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("ai_base_url")
        val encryptedApiKey = stringPreferencesKey("ai_api_key_encrypted")
        val model = stringPreferencesKey("ai_model")
        val userId = stringPreferencesKey("stable_user_id")
    }

    val aiConfig: Flow<AiConfig> = context.settingsDataStore.data.map { preferences ->
        AiConfig(
            baseUrl = preferences[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: AiDefaults.BASE_URL,
            apiKey = preferences[Keys.encryptedApiKey]?.let(::decrypt)?.takeIf { it.isNotBlank() } ?: AiDefaults.API_KEY,
            model = preferences[Keys.model]?.takeIf { it.isNotBlank() } ?: AiDefaults.MODEL,
            stableUserId = preferences[Keys.userId].orEmpty(),
        )
    }

    suspend fun saveAiConfig(config: AiConfig) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.baseUrl] = config.baseUrl.trim()
            preferences[Keys.encryptedApiKey] = encrypt(config.apiKey)
            preferences[Keys.model] = config.model.trim()
            preferences[Keys.userId] = config.stableUserId.ifBlank { "yishi-${UUID.randomUUID()}" }
        }
    }

    private fun secretKey(): SecretKey {
        val alias = "yishi-ai-key"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}

data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val stableUserId: String,
)
