package com.kyf778.yishi.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 备份包：一次导出包含全部实体。
 *
 * 为什么要备份：
 *   忆时的数据**只存在手机本地**（/data/data/<包名>/databases/yishi.db），
 *   没有任何服务器。所以「清除应用数据」「卸载重装」「换手机」这三种操作
 *   都会让卡片彻底消失——开发时执行过一次 `pm clear` 就丢了一整批数据。
 *
 * 做法：
 *   把数据库全部内容序列化成一个 JSON，写到你**自己选的文件夹**里
 *   （通过 SAF 选目录，比如「下载」目录，也可以选网盘客户端的同步目录）。
 *   重装后再从同一个文件导回来。
 *
 *   选 SAF 而不是直接写 /sdcard 是因为 Android 10+ 的分区存储：
 *   直接写任意路径需要额外权限，而 SAF 由用户授权一次即长期有效，且能跨 App 重装保留 URI 授权。
 */
@Serializable
data class BackupPayload(
    /** 备份格式版本，将来改结构时用于兼容处理。 */
    val schemaVersion: Int = 1,
    val appVersion: String = "0.9.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val memories: List<MemoryItemEntity> = emptyList(),
    val sources: List<SourceMaterialEntity> = emptyList(),
    val events: List<CountdownEventEntity> = emptyList(),
    val reminders: List<ReminderRuleEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val memoryTags: List<MemoryTagCrossRef> = emptyList(),
) {
    val itemCount: Int get() = events.size
}

@Serializable
data class BackupResult(
    val ok: Boolean,
    val message: String,
    val itemCount: Int = 0,
    val bytes: Long = 0,
)

class BackupManager(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.memoryDao()
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** 把整库导出成一个 JSON 字节数组。 */
    suspend fun exportPayload(): ByteArray = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            appVersion = appVersionName(),
            memories = dao.dumpMemories(),
            sources = dao.dumpSources(),
            events = dao.dumpEvents(),
            reminders = dao.dumpReminders(),
            tags = dao.dumpTags(),
            memoryTags = dao.dumpMemoryTags(),
        )
        json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
    }

    /** 导出到用户选定的目录（SAF 返回的 tree uri）。文件名带时间戳，便于留多份。 */
    suspend fun exportTo(treeUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = exportPayload()
            val name = "yishi-backup-${fileStamp()}.json"
            val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri),
            )
            val created = android.provider.DocumentsContract.createDocument(
                context.contentResolver, doc, "application/json", name,
            ) ?: error("无法在所选目录创建文件")
            context.contentResolver.openOutputStream(created)?.use { it.write(bytes) }
                ?: error("无法写入所选目录")
            prefs().edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            BackupResult(true, "已备份：$name", countItems(), bytes.size.toLong())
        }.getOrElse { BackupResult(false, "备份失败：${it.message ?: it::class.java.simpleName}") }
    }

    /**
     * 同时留一份在 App 自己的目录里（不需要任何权限）。
     *
     * 它的作用：
     *   1. 兜底：用户没选 SAF 目录时也至少有份可回滚的副本；
     *   2. 可验证：这份文件开发者能用 adb 直接读出来检查内容是否完整。
     *
     * 注意它**不能**抗卸载/清数据（那一并删除），抗卸载的是 SAF 目录那份。
     */
    suspend fun exportToInternal(): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = exportPayload()
            val dir = File(context.filesDir, "backup").apply { mkdirs() }
            val f = File(dir, "latest.json")
            f.writeBytes(bytes)
            // 只保留最近 3 份带时间戳的快照，避免无限增长
            val snap = File(dir, "yishi-${fileStamp()}.json")
            snap.writeBytes(bytes)
            dir.listFiles { _, n -> n.startsWith("yishi-") }
                ?.sortedByDescending { it.name }
                ?.drop(3)
                ?.forEach { it.delete() }
            BackupResult(true, "已备份到应用内部目录", countItems(), bytes.size.toLong())
        }.getOrElse { BackupResult(false, "内部备份失败：${it.message ?: it::class.java.simpleName}") }
    }

    /** 内部备份文件（用于开发期检查内容）。 */
    fun internalBackupFile(): File = File(File(context.filesDir, "backup"), "latest.json")

    /**
     * 自动备份到**公共目录**（Downloads/Yishi），不需要用户选任何东西、也不需要任何权限。
     *
     * 为什么用 MediaStore 而不是直接写文件：
     *   Android 10+ 的分区存储下，直接写 /sdcard/Download 需要 MANAGE_EXTERNAL_STORAGE
     *   这种敏感权限；而 MediaStore 的 Downloads 集合是官方给「App 自己产生的、
     *   想让用户看得见」的文件准备的通道，零权限即可写入。
     *
     * 为什么放公共目录：
     *   App 私有目录会随卸载/清数据一起删除；公共目录不会被删，
     *   所以卸载重装后这份备份还在，能直接导回来。
     *
     * 用固定文件名，每次覆盖 —— 避免目录里堆出几十个备份文件。
     */
    suspend fun exportToDownloads(): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = exportPayload()
            val resolver = context.contentResolver
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val name = AUTO_BACKUP_NAME

            // 先找有没有已存在的同名文件，有就复用（覆盖写），没有才新建。
            // 复用能让「文件大小/时间」保持一个，用户不会看到一堆副本。
            var target: Uri? = null
            resolver.query(
                collection,
                arrayOf(android.provider.MediaStore.Downloads._ID),
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(name),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    target = android.content.ContentUris.withAppendedId(collection, id)
                }
            }

            val uri = target ?: resolver.insert(
                collection,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                    // 放在 Downloads/Yishi 子目录，不跟其它下载混在一起
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Yishi")
                },
            ) ?: error("无法创建备份文件")

            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("无法写入备份文件")

            prefs().edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            BackupResult(true, "已自动备份到「下载/Yishi」", countItems(), bytes.size.toLong())
        }.getOrElse { BackupResult(false, "自动备份失败：${it.message ?: it::class.java.simpleName}") }
    }

    /**
     * 从自动备份恢复。这是「卸载重装后找回数据」的入口，不需要用户选文件。
     */
    suspend fun importFromDownloads(): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            var target: Uri? = null
            resolver.query(
                collection,
                arrayOf(android.provider.MediaStore.Downloads._ID),
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(AUTO_BACKUP_NAME),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    target = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                }
            }
            val uri = target ?: return@runCatching BackupResult(false, "没找到自动备份文件")
            val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("无法读取备份文件")
            val payload = json.decodeFromString(BackupPayload.serializer(), text)
            if (payload.memories.isEmpty() && payload.events.isEmpty()) {
                return@runCatching BackupResult(false, "备份文件里没有数据")
            }
            applyPayload(payload)
            BackupResult(true, "已恢复 ${payload.events.size} 个事项", payload.events.size, text.length.toLong())
        }.getOrElse { BackupResult(false, "恢复失败：${it.message ?: it::class.java.simpleName}") }
    }

    /** 自动备份文件在公共目录里是否存在、多大。用于设置页显示状态。 */
    fun autoBackupInfo(): Triple<Boolean, Long, Long> {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var size = 0L
        var modified = 0L
        resolver.query(
            collection,
            arrayOf(
                android.provider.MediaStore.Downloads.SIZE,
                android.provider.MediaStore.Downloads.DATE_MODIFIED,
            ),
            "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(AUTO_BACKUP_NAME),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                size = c.getLong(0)
                modified = c.getLong(1) * 1000L
            }
        }
        return Triple(size > 0, size, modified)
    }

    /** 把备份内容写回数据库（恢复与导入共用）。 */
    private suspend fun applyPayload(payload: BackupPayload) {
        db.withTransaction {
            // 子表先删（外键约束），再删主表
            dao.clearNotificationLogs()
            dao.clearAiRecords()
            dao.clearReminders()
            dao.clearEvents()
            dao.clearMemoryTags()
            dao.clearTags()
            dao.clearSources()
            dao.clearMemories()
            // 再按原 id 写回，顺序同样是主表在前
            if (payload.memories.isNotEmpty()) dao.restoreMemories(payload.memories)
            if (payload.sources.isNotEmpty()) dao.restoreSources(payload.sources)
            if (payload.events.isNotEmpty()) dao.restoreEvents(payload.events)
            if (payload.reminders.isNotEmpty()) dao.restoreReminders(payload.reminders)
            if (payload.tags.isNotEmpty()) dao.restoreTags(payload.tags)
            if (payload.memoryTags.isNotEmpty()) dao.restoreMemoryTags(payload.memoryTags)
        }
    }

    /** 记下用户选的目录，下次自动备份时直接用它（不弹框）。 */
    fun saveTreeUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs().edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun savedTreeUri(): Uri? = prefs().getString(KEY_TREE, null)?.let(Uri::parse)

    fun clearTreeUri() {
        prefs().edit().remove(KEY_TREE).apply()
    }

    fun autoBackupEnabled(): Boolean = prefs().getBoolean(KEY_AUTO, true)
    fun setAutoBackup(enabled: Boolean) = prefs().edit().putBoolean(KEY_AUTO, enabled).apply()

    fun lastBackupAt(): Long = prefs().getLong(KEY_LAST, 0L)

    /** 从用户选的文件恢复。会先清空现有数据，再整表写回。 */
    suspend fun importFrom(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("无法读取所选文件")
            val payload = json.decodeFromString(BackupPayload.serializer(), text)
            if (payload.memories.isEmpty() && payload.events.isEmpty()) {
                return@runCatching BackupResult(false, "这个备份文件里没有数据")
            }
            applyPayload(payload)
            BackupResult(true, "已恢复 ${payload.events.size} 个事项", payload.events.size, text.length.toLong())
        }.getOrElse {
            BackupResult(false, "恢复失败：${it.message ?: it::class.java.simpleName}")
        }
    }

    private suspend fun countItems(): Int = dao.dumpEvents().size

    private fun prefs() = context.getSharedPreferences("yishi_backup", Context.MODE_PRIVATE)

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun fileStamp(): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        return fmt.format(java.util.Date())
    }

    companion object {
        private const val KEY_TREE = "tree_uri"
        private const val KEY_AUTO = "auto_backup"
        private const val KEY_LAST = "last_backup_at"

        /** 自动备份的固定文件名（放 Download/Yishi 下，每次覆盖）。 */
        const val AUTO_BACKUP_NAME = "yishi-auto-backup.json"

        /** 备份文件建议名字，用于「保存到下载」这类系统入口。 */
        fun suggestedFileName(): String {
            val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            return "yishi-backup-${fmt.format(java.util.Date())}.json"
        }
    }
}
