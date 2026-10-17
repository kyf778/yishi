package com.kyf778.yishi.ai

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 解析 AI 返回的截止时间字符串。
 *
 * 为什么需要这个：模型（尤其是视觉模型）经常返回非标准 ISO 格式——
 * 纯日期 "2026-11-05"、空格分隔 "2026-11-05 11:00"、斜杠 "2026/11/05 11:00"、
 * 中文 "2026年11月5日 11:00"。之前只接受 OffsetDateTime/LocalDateTime，
 * 这 4 类全部直接报「无法识别有效截止时间」，白白浪费一次识别。
 *
 * 只有日期没有时间时，按当天 23:59 处理（"11月5日前提交" 的自然理解）。
 * 绝不凭空补年份或猜日期。
 */
object DeadlineParser {

    private val LOOSE_DATE = DateTimeFormatter.ofPattern("yyyy-M-d")
    private val CJK_RE = Regex("[年月日/.]")

    /** 手机中文输入法常产生全角字符（－：／．和全角数字），先归一化成半角。 */
    private val FULLWIDTH = mapOf(
        '－' to '-', '：' to ':', '／' to '/', '．' to '.', '　' to ' ',
        '０' to '0', '１' to '1', '２' to '2', '３' to '3', '４' to '4',
        '５' to '5', '６' to '6', '７' to '7', '８' to '8', '９' to '9',
    )

    private fun normalizeFullwidth(value: String): String =
        buildString(value.length) { value.forEach { append(FULLWIDTH[it] ?: it) } }

    fun parse(value: String?, timezoneHint: String? = null): Long? {
        val raw = normalizeFullwidth(value?.trim().orEmpty())
        if (raw.isBlank()) return null
        val zone = resolveZone(timezoneHint)

        // 1) 完整 ISO-8601（含偏移或 Z）—— 最可信，直接用
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }

        // 2) 无偏移的 ISO 本地日期时间，按提示时区解释
        runCatching { LocalDateTime.parse(raw).atZone(zone).toInstant().toEpochMilli() }.getOrNull()?.let { return it }

        // 3) 纯日期（含单位数月日，如 2026-1-5）-> 当天 23:59
        runCatching {
            LocalDate.parse(raw, LOOSE_DATE)
                .atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }

        // 4) 宽松格式：空格分隔、斜杠、中文年月日
        val hasLooseMarker = CJK_RE.containsMatchIn(raw) || raw.contains('/') || raw.contains(' ')
        if (!hasLooseMarker) return null
        val cleaned = raw
            .replace("年", "-").replace("月", "-").replace("日", " ")
            .replace('/', '-').replace('.', '-')
            .replace('T', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-')

        val datePart = cleaned.substringBefore(' ').trim()
        val timePart = cleaned.substringAfter(' ', "").trim()
        val date = runCatching { LocalDate.parse(datePart, LOOSE_DATE) }.getOrNull() ?: return null
        val time = if (timePart.isBlank()) {
            LocalTime.of(23, 59)
        } else {
            parseLooseTime(timePart) ?: LocalTime.of(23, 59)
        }
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseLooseTime(text: String): LocalTime? {
        val normalized = text.replace('.', ':')
        val parts = normalized.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            1 -> runCatching { LocalTime.of(parts[0].coerceIn(0, 23), 0) }.getOrNull()
            2 -> runCatching { LocalTime.of(parts[0].coerceIn(0, 23), parts[1].coerceIn(0, 59)) }.getOrNull()
            3 -> runCatching {
                LocalTime.of(parts[0].coerceIn(0, 23), parts[1].coerceIn(0, 59), parts[2].coerceIn(0, 59))
            }.getOrNull()
            else -> null
        }
    }

    private fun resolveZone(hint: String?): ZoneId =
        runCatching { hint?.let { ZoneId.of(it.trim()) } }.getOrNull() ?: ZoneId.systemDefault()
}
