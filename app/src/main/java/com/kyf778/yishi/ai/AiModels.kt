package com.kyf778.yishi.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AiInput(
    val text: String,
    val imageDataUrl: String? = null,
    val currentTime: String,
    val timezone: String,
    /** 长截图切成多块时使用；非空则忽略 imageDataUrl，按顺序作为多张图发送。 */
    val imageDataUrls: List<String> = emptyList(),
    /**
     * true 时要求模型返回**所有**未来事项（JSON 数组），供用户在确认页勾选要建哪些；
     * false 时只要一个（旧行为）。
     */
    val allEvents: Boolean = false,
) {
    val allImageDataUrls: List<String>
        get() = imageDataUrls.ifEmpty { listOfNotNull(imageDataUrl) }
}

@Serializable
data class ReminderSuggestion(
    val offsetMinutes: Int = 60,
    val reason: String = "",
)

@Serializable
data class ExtractedMemory(
    val title: String = "",
    val summary: String = "",
    val deadline: String? = null,
    val timezone: String? = null,
    val eventType: String = "OTHER",
    val location: String? = null,
    val notes: String? = null,
    val reminderSuggestions: List<ReminderSuggestion> = emptyList(),
    val confidence: Double = 0.0,
    val evidence: List<String> = emptyList(),
)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatResponseMessage? = null)

@Serializable
data class ChatResponseMessage(
    val role: String? = null,
    val content: JsonElement? = null,
)

@Serializable
data class ModelList(val data: List<ModelRow> = emptyList())

@Serializable
data class ModelRow(
    val id: String = "",
    @SerialName("object") val objectType: String? = null,
)
