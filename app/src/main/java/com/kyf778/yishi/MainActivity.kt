package com.kyf778.yishi

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyf778.yishi.ai.AiInput
import com.kyf778.yishi.ai.AiProvider
import com.kyf778.yishi.ai.CodeBuddyDirectProvider
import com.kyf778.yishi.ai.DeadlineParser
import com.kyf778.yishi.ai.ExtractedMemory
import com.kyf778.yishi.ai.OpenAiCompatibleProvider
import com.kyf778.yishi.data.AiRecordDraft
import com.kyf778.yishi.data.AppDatabase
import com.kyf778.yishi.data.BackupManager
import com.kyf778.yishi.data.CountdownCard
import com.kyf778.yishi.data.MemoryRepository
import com.kyf778.yishi.data.SourceDraft
import com.kyf778.yishi.reminder.ReminderReceiver
import com.kyf778.yishi.reminder.ReminderScheduler
import com.kyf778.yishi.settings.AiConfig
import com.kyf778.yishi.settings.AiDefaults
import com.kyf778.yishi.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YishiApp() }
    }

    /**
     * App 已经在前台/后台时点了通知 —— 此时不会走 onCreate，只会走这里。
     *
     * Activity 默认 launchMode 是 standard，正常情况下点通知会新建实例；
     * 但如果系统复用了已有任务栈（或被 Launcher 拉回），就必须在这里把
     * 新 Intent 换进来，否则 Compose 里读到的还是旧 Intent，跳不到详情。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun YishiApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.get(context) }
    val repository = remember { MemoryRepository(database) }
    val scheduler = remember { ReminderScheduler(context, repository) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // 详情页：存 eventId 而不是对象，这样旋转屏幕后能恢复
    // （CountdownCard 是普通 data class，不能直接 rememberSaveable）
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<CountdownCard?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val cards by repository.observeCountdowns().collectAsStateWithLifecycle(emptyList())
    // 由 selectedId 反查卡片；cards 变化时自动跟随
    val selected: CountdownCard? = selectedId?.let { id -> cards.firstOrNull { it.eventId == id } }
    val pastCards by repository.observePastEvents().collectAsStateWithLifecycle(emptyList())
    val statuses by repository.observeStatusCounts().collectAsStateWithLifecycle(emptyList())
    val types by repository.observeTypeCounts().collectAsStateWithLifecycle(emptyList())
    val logs by repository.observeNotificationLogs().collectAsStateWithLifecycle(emptyList())
    // 已有标签（首页筛选条用）
    val tagEntities by repository.observeTags().collectAsStateWithLifecycle(emptyList())
    // 批量操作选中的 eventId
    var batchSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // 待确认的动作（防止误删/误完成）：null 表示没有待确认项
    var deleteTarget by remember { mutableStateOf<CountdownCard?>(null) }
    var completeTarget by remember { mutableStateOf<CountdownCard?>(null) }
    var batchDeletePending by remember { mutableStateOf(false) }
    var batchCompletePending by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<CountdownCard?>(null) }
    var supplementTarget by remember { mutableStateOf<CountdownCard?>(null) }

    // 进入前台就刷新逾期状态并重新排提醒：只跑一次的话，App 长期挂在后台时
    // 事项会一直显示"还剩 x 天"而不会变成"已逾期"。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            repository.markOverdue()
            ReminderReceiver.createChannels(context)
        }
    }
    LaunchedEffect(Unit) {
        // 先补「缺了提醒规则」的事项（迁移清过旧规则、或历史数据不全时靠这个兜底），
        // 再把所有未来提醒重新排进 WorkManager。
        // 顺序不能反：rescheduleAll 只处理已存在的规则，不会凭空创建。
        runCatching { repository.repairMissingReminders() }
        scheduler.rescheduleAll()
    }

    // 从通知点进来时，直接打开对应事项的详情页。
    //
    // 之前 ReminderWorker 把 eventId 塞进了 Intent（EXTRA_EVENT_ID），
    // 但这里从来没人读 —— 点通知只会停在首页，用户还得自己翻列表找。
    //
    // 注意用 Activity 的 intent 而不是 CompositionLocal：外部启动时
    // LocalContext 拿到的可能不是 Activity 实例。
    val activity = context as? android.app.Activity
    LaunchedEffect(cards) {
        val intent = activity?.intent ?: return@LaunchedEffect
        val eventId = intent.getLongExtra(MainActivity.EXTRA_EVENT_ID, -1L)
        if (eventId < 0L) return@LaunchedEffect
        // 等数据加载出来再找；找到就打开详情，然后清掉这个 extra，
        // 否则旋转屏幕/再次回到前台又会重新弹一次详情。
        val target = cards.firstOrNull { it.eventId == eventId }
        if (target != null) {
            selectedId = target.eventId
            intent.removeExtra(MainActivity.EXTRA_EVENT_ID)
        } else if (cards.isNotEmpty()) {
            // 数据已经加载完但没这条（可能已被删除），别一直等着
            intent.removeExtra(MainActivity.EXTRA_EVENT_ID)
        }
    }

    MaterialTheme {
        Surface(color = Color(0xFFF6F7FB), modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when (tab) {
                                    0 -> "未来倒计时"
                                    1 -> "统计数据"
                                    else -> "提醒记录"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        actions = {
                            // AI 识别放在标题栏，常驻可见，不再和"提醒已启用"那种死按钮并排占位置
                            if (tab == 0) {
                                IconButton(onClick = { showImport = true }) {
                                    Icon(Icons.Default.AutoAwesome, "AI 识别", tint = Color(0xFF4F6BFF))
                                }
                            }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "设置") }
                        },
                    )
                },
                bottomBar = {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(onClick = { tab = 0 }) { Text("倒计时", color = if (tab == 0) Color(0xFF4F6BFF) else Color.Gray) }
                        TextButton(onClick = { tab = 1 }) { Text("统计", color = if (tab == 1) Color(0xFF4F6BFF) else Color.Gray) }
                        TextButton(onClick = { tab = 2 }) { Text("提醒记录", color = if (tab == 2) Color(0xFF4F6BFF) else Color.Gray) }
                    }
                },
                floatingActionButton = {
                    if (tab == 0) FloatingActionButton(onClick = { showAdd = true }, containerColor = Color(0xFF4F6BFF)) {
                        Icon(Icons.Default.Add, "新增", tint = Color.White)
                    }
                },
            ) { padding ->
                when (tab) {
                    0 -> CountdownHome(
                        padding = padding,
                        cards = cards,
                        selected = batchSelected,
                        onToggleSelect = { eventId ->
                            batchSelected = if (eventId in batchSelected) batchSelected - eventId else batchSelected + eventId
                        },
                        onClearSelection = { batchSelected = emptySet() },
                        onBatchDone = { batchCompletePending = true },
                        onBatchDelete = { batchDeletePending = true },
                        onSelect = { selectedId = it.eventId },
                        onImport = { showImport = true },
                    )
                    1 -> StatisticsPage(
                        padding, statuses, types, pastCards,
                        onRestore = { restoreTarget = it },
                    )
                    else -> NotificationLogPage(padding, logs)
                }
            }
        }
    }

    if (showAdd) {
        ManualAddDialog(
            onDismiss = { showAdd = false },
            onSave = { title, summary, deadline, dateOnly ->
                scope.launch {
                    val eventId = repository.createManualCountdown(
                        title, summary, deadline, dateOnly = dateOnly,
                    )
                    repository.futureReminders().filter { it.eventId == eventId }.forEach(scheduler::schedule)
                    showAdd = false
                }
            },
        )
    }
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onSaved = { showImport = false },
        )
    }
    selected?.let { card ->
        DetailDialog(
            card = card,
            onDismiss = { selectedId = null },
            onComplete = { completeTarget = card },
            onEdit = { editing = card; selectedId = null },
            onDelete = {
                deleteTarget = card
            },
            onSupplement = { supplementTarget = card },
        )
    }
    editing?.let { card ->
        EditDialog(
            card = card,
            onDismiss = { editing = null },
            onSave = { title, summary, deadline, dateOnly ->
                scope.launch {
                    repository.updateEvent(
                        card.eventId, title, summary, deadline, dateOnly = dateOnly,
                    )
                    scheduler.rescheduleAll()
                    editing = null
                }
            },
        )
    }

    // ════════ 确认对话框（防误操作）════════

    // 删除单条
    deleteTarget?.let { card ->
        ConfirmDialog(
            title = "删除这个事项？",
            message = "「${card.title}」将从列表移除，可在「统计 → 已完成」里找回。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch { repository.delete(card.eventId); selectedId = null }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    // 完成单条
    completeTarget?.let { card ->
        ConfirmDialog(
            title = "标记为已完成？",
            message = "「${card.title}」会移到「统计 → 已完成」，之后可以恢复。",
            confirmText = "已完成",
            danger = false,
            onConfirm = {
                scope.launch { repository.complete(card.eventId); selectedId = null }
                completeTarget = null
            },
            onDismiss = { completeTarget = null },
        )
    }

    // 批量删除
    if (batchDeletePending) {
        ConfirmDialog(
            title = "删除 ${batchSelected.size} 个事项？",
            message = "这些事项将从列表移除，可在「统计 → 已完成」里找回。",
            confirmText = "全部删除",
            danger = true,
            onConfirm = {
                val ids = batchSelected
                batchSelected = emptySet()
                batchDeletePending = false
                scope.launch { repository.deleteMany(ids) }
            },
            onDismiss = { batchDeletePending = false },
        )
    }

    // 批量完成
    if (batchCompletePending) {
        ConfirmDialog(
            title = "标记 ${batchSelected.size} 个为已完成？",
            message = "它们会移到「统计 → 已完成」，之后可以恢复。",
            confirmText = "全部已完成",
            danger = false,
            onConfirm = {
                val ids = batchSelected
                batchSelected = emptySet()
                batchCompletePending = false
                scope.launch { repository.completeMany(ids) }
            },
            onDismiss = { batchCompletePending = false },
        )
    }

    // 补充信息
    supplementTarget?.let { card ->
        SupplementDialog(
            card = card,
            onDismiss = { supplementTarget = null },
            onApplied = { supplementTarget = null },
        )
    }

    // 恢复
    restoreTarget?.let { card ->
        ConfirmDialog(
            title = "恢复这个事项？",
            message = "「${card.title}」会回到倒计时列表。若截止时间还没到，提醒也会重新排上。",
            confirmText = "恢复",
            danger = false,
            onConfirm = {
                scope.launch { repository.restore(card.eventId) }
                restoreTarget = null
            },
            onDismiss = { restoreTarget = null },
        )
    }

    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CountdownHome(
    padding: PaddingValues,
    cards: List<CountdownCard>,
    selected: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onBatchDone: () -> Unit,
    onBatchDelete: () -> Unit,
    onSelect: (CountdownCard) -> Unit,
    onImport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    // 分钟级时钟：同一分钟内不重组，避免每秒刷整页（性能关键）
    val nowMinute = rememberMinuteTick() * 60_000L

    // 只按搜索词过滤（标题+备注），固定按截止时间排序
    val visible = remember(cards, query) {
        val q = query.trim()
        var filtered = cards
        if (q.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true)
            }
        }
        // 按截止时间升序（最早的排最前）。
        // 纯日期事项按「当天 00:00」参与排序 —— 它的 23:59 只是占位，
        // 拿占位值排序会把它挤到同日有钟点事项的后面，而"这天做完"应该更靠前。
        filtered.sortedBy { card ->
            if (card.isDateOnly()) {
                val zone = ZoneId.systemDefault()
                val d = java.time.Instant.ofEpochMilli(card.deadlineAt).atZone(zone).toLocalDate()
                d.atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                card.deadlineAt
            }
        }
    }

    val inSelectMode = selected.isNotEmpty()

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有未来事项", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("点右下角 + 新增，或用 AI 识别截图", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onImport,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6BFF)),
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 识别截图")
                    }
                }
            }
            return@Column
        }

        // 搜索 + 排序条：紧凑高度（56dp 一行），上下留白收窄
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索标题或备注", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            )
        }

        // 批量操作条（有选中项时出现）
        if (inSelectMode) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .background(Color(0xFFEEF1FF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已选 ${selected.size} 项",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                // TextButton 自带上下 12dp 内边距，用 contentPadding 压扁
                BatchAction("完成", Color(0xFF4F6BFF), onBatchDone)
                BatchAction("删除", Color(0xFFD55245), onBatchDelete)
                BatchAction("取消", Color(0xFF7C8495), onClearSelection)
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "没有匹配「$query」的事项",
                    color = Color.Gray, fontSize = 14.sp,
                )
            }
            return@Column
        }

        LazyColumn(
            // 批量模式下左侧让出 26dp 给对勾，卡片不缩进就压住内容了
            contentPadding = PaddingValues(
                start = if (inSelectMode) 4.dp else 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 90.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(visible, key = { it.eventId }) { card ->
                val inBatch = card.eventId in selected
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 选中标记：卡片左侧**独立一列**，绝不压内容
                    if (inSelectMode) {
                        Icon(
                            if (inBatch) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (inBatch) Color(0xFF4F6BFF) else Color(0xFFC5CBD8),
                            modifier = Modifier
                                .padding(start = 6.dp, end = 6.dp)
                                .size(24.dp),
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .combinedClickable(
                                // 长按进入批量模式（并选中这一张）
                                onLongClick = { onToggleSelect(card.eventId) },
                                // 批量模式下单击=勾选/取消；正常模式单击=看详情
                                onClick = {
                                    if (inSelectMode) onToggleSelect(card.eventId) else onSelect(card)
                                },
                            )
                    ) {
                        // 选中时的蓝色描边（画在卡片上，不占布局）
                        if (inSelectMode && inBatch) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .padding(1.dp)
                                    .border(2.5.dp, Color(0xFF4F6BFF), RoundedCornerShape(20.dp)),
                            )
                        }
                        CountdownCardView(card, nowMinute)
                    }
                }
            }
        }
    }
}


/**
 * 统一的二次确认对话框。
 * 删除这类不可逆操作用 danger=true（红色按钮），完成/恢复用普通色。
 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = { Text(message, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (danger) Color(0xFFD55245) else Color(0xFF4F6BFF),
                ),
            ) { Text(confirmText, color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 批量操作条里的文字按钮：压扁内边距，让整条只有约 36dp 高。 */
@Composable
private fun BatchAction(text: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 13.sp, color = color)
    }
}

/** 每类事项配一套渐变，视觉上区分类型，也避免整屏都是同一个颜色。 */
private data class CardTheme(val colors: List<Color>, val accent: Color, val decor: String)

private fun themeFor(eventType: String, overdue: Boolean): CardTheme {
    if (overdue) return CardTheme(
        listOf(Color(0xFF8E2A24), Color(0xFFC0392B), Color(0xFFE05B4A)),
        Color(0xFFFFD5CF), "warn",
    )
    return when (eventType) {
        "EXAM" -> CardTheme(listOf(Color(0xFF1B2A4A), Color(0xFF2C4270), Color(0xFF3E5C96)), Color(0xFFBBD0FF), "moon")
        "SUBMISSION" -> CardTheme(listOf(Color(0xFF3A2A55), Color(0xFF6A4A8C), Color(0xFF9B6FB8)), Color(0xFFE8D5FF), "star")
        "MEETING" -> CardTheme(listOf(Color(0xFF14413C), Color(0xFF1F6B62), Color(0xFF2E9184)), Color(0xFFB8F0E6), "dot")
        "INTERVIEW" -> CardTheme(listOf(Color(0xFF5A3418), Color(0xFF8C5528), Color(0xFFC07A3E)), Color(0xFFFFE0B8), "star")
        else -> CardTheme(listOf(Color(0xFF232C46), Color(0xFF3A4767), Color(0xFF54648C)), Color(0xFFC8D4F0), "dot")
    }
}

// ══════════════════════════════════════════════════════════════
//  纯日期事项的统一处理
//
//  核心概念：纯日期事项（dateOnly=true）的 deadlineAt 只是一个"占位"，
//  真正有意义的只有**日期**。任何按毫秒差、按小时、按分钟的算法都算错了。
//
//  所以所有跟时间相关的显示，都必须先问一句「这是纯日期吗」，
//  走两条完全不同的分支。
// ══════════════════════════════════════════════════════════════

/**
 * 从用户输入的时间文本判断「是否只精确到日」。
 *
 * 手动新增/编辑用的也是同一个宽容解析器，输入「2026-10-20」会被补成 23:59，
 * 但那只是占位 —— 用户明显没写钟点，就该按纯日期处理。
 *
 * 判断依据：文本里有没有钟点（HH:mm、点、时、分）。
 */
private fun inferDateOnlyFromInput(text: String): Boolean {
    val s = text.trim()
    if (s.isBlank()) return false
    // 有 ":" 或 "：" 视为有时刻
    if (s.contains(":") || s.contains("：")) return false
    // 有「点/时/分」也视为有时刻
    if (s.contains("点") || s.contains("时") || s.contains("分")) return false
    // 有 am/pm 也视为有时刻
    if (s.contains("am", ignoreCase = true) || s.contains("pm", ignoreCase = true)) return false
    return true
}

/** 纯日期事项距离今天还有几天（0=今天，1=明天，负数=已过）。 */
private fun daysUntil(dateOnlyMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val target = java.time.Instant.ofEpochMilli(dateOnlyMillis).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return java.time.temporal.ChronoUnit.DAYS.between(today, target)
}

/**
 * 纯日期事项的「剩余」显示 —— 只给天数，绝不给小时。
 *
 *  今天   → ("今天", "")
 *  明天   → ("1", "天")
 *  后天   → ("2", "天")
 *  已过 N 天 → ("已过", "N 天")
 *
 * 返回 (数字, 单位)，与 formatRemaining 的签名兼容，但单位可能是空串。
 */
private fun formatRemainingDateOnly(millis: Long): Pair<String, String> {
    val d = daysUntil(millis)
    return when {
        d == 0L -> "今天" to ""
        d > 0L -> "$d" to "天"
        else -> "已过" to "${-d} 天"
    }
}

/**
 * 纯日期事项的卡片右上角标签。
 * 今天截止的不写「还剩」，直接说「今天」。
 */
private fun dateOnlyLabel(millis: Long): String {
    val d = daysUntil(millis)
    return when {
        d == 0L -> "今天"
        d > 0L -> "还剩"
        else -> "已过期"
    }
}

/**
 * 把剩余时间格式化成「数字 + 单位」。
 *
 * 不能只按天/小时显示：一个 10 分钟后的事项会显示成「0 小时」，
 * 用户根本不知道还剩多久。所以按量级自动选单位。
 */
private fun formatRemaining(millis: Long): Pair<String, String> {
    val abs = kotlin.math.abs(millis)
    val minutes = abs / 60_000L
    val hours = abs / 3_600_000L
    val days = abs / 86_400_000L
    return when {
        minutes < 1L -> "0" to "分钟"
        minutes < 60L -> "$minutes" to "分钟"
        hours < 24L -> "$hours" to "小时"
        else -> "$days" to "天"
    }
}

/**
 * 判断这个截止时间是不是"只精确到天"。
 *
 * 约定：DeadlineParser 把纯日期（如"10月20日"）补成当天 23:59。
 * 所以看到 23:59 就说明原始信息里没有具体钟点，显示时不该把这个补出来的
 * 假时间露出来——用户看到"后天 23:59"会以为真有人在那个点截止。
 */
private fun isDayOnly(value: Long): Boolean {
    val t = java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    return t.hour == 23 && t.minute == 59 && t.second == 0
}

/**
 * 卡片是否只精确到日。
 * 优先用数据库里的 dateOnly 字段（LLM/用户显式标记），
 * 兜底才用「时间是不是 23:59」去猜（兼容老数据）。
 */
private fun CountdownCard.isDateOnly(): Boolean = dateOnly || isDayOnly(deadlineAt)

/** 「今天 14:30」「明天」「10月21日 16:00」「10月21日」——比纯数字更直观。 */
private fun formatDeadlineFriendly(value: Long, forceDateOnly: Boolean = false): String {
    val zone = ZoneId.systemDefault()
    val target = java.time.Instant.ofEpochMilli(value).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    // 纯日期：一律显示「10月20日」，不写相对说法，也不写任何钟点。
    if (forceDateOnly || isDayOnly(value)) {
        return target.format(DateTimeFormatter.ofPattern("M月d日"))
    }
    val time = target.format(DateTimeFormatter.ofPattern(" HH:mm"))
    return when (target.toLocalDate()) {
        today -> "今天$time"
        today.plusDays(1) -> "明天$time"
        today.plusDays(2) -> "后天$time"
        else -> target.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }
}

/**
 * 每秒触发一次的重组时钟。
 *
 * 为什么需要：倒计时卡片里的 `System.currentTimeMillis()` 只在**重组时**读一次，
 * 没有东西驱动重组的话，界面上的"还剩 N 天/小时/分钟"就会一直停在初始化那一刻，
 * 看起来就像卡住了。
 *
 * 用 produceState + delay 做节拍器。返回的值本身不重要，重要的是它每次变化
 * 都会让读它的 Composable 重组一次，从而重新读取当前时间。
 *
 * 只精确到秒（1000ms），不用 16ms —— 倒计时按秒/分变化，没必要按帧刷新。
 */
@Composable
private fun rememberSecondTick(): Long {
    val tick by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    return tick
}

/**
 * 分钟级的时钟。
 *
 * 倒计时卡片显示的粒度最细到"分钟"，所以**不需要每秒重组**。
 * 用 derivedStateOf 把秒级 tick 压成分钟级：同一分钟内 value 不变，
 * 读它的 Composable 就不会重组，省掉 59/60 的无用渲染。
 *
 * 这一条是性能关键：之前每秒全页重组导致 Janky 50%。
 */
@Composable
private fun rememberMinuteTick(): Long {
    val secTick = rememberSecondTick()
    val minuteTick by remember {
        derivedStateOf { secTick / 60_000L }
    }
    return minuteTick
}

@Composable
private fun CountdownCardView(card: CountdownCard, now: Long) {
    val remaining = card.deadlineAt - now
    val overdue = remaining < 0L || card.status == "OVERDUE"
    val totalMs = card.deadlineAt - card.createdAt
    val elapsedMs = now - card.createdAt

    // 纯日期事项不按毫秒算，只按「还有几天」
    val isDateOnly = card.isDateOnly()
    val (remainValue, remainUnit) = if (isDateOnly) {
        formatRemainingDateOnly(card.deadlineAt)
    } else {
        formatRemaining(remaining)
    }
    // 「已过 X/Y 天」：X 是已过天数，Y 是创建到截止的总天数
    // 纯日期事项按「日历日差」算，避免 23:59 占位值让进度差一天
    val totalDays: Long
    val elapsedDays: Long
    if (isDateOnly) {
        val zone = ZoneId.systemDefault()
        val createdDate = java.time.Instant.ofEpochMilli(card.createdAt.let { if (it > 0) it else now })
            .atZone(zone).toLocalDate()
        val deadlineDate = java.time.Instant.ofEpochMilli(card.deadlineAt).atZone(zone).toLocalDate()
        totalDays = maxOf(1L, java.time.temporal.ChronoUnit.DAYS.between(createdDate, deadlineDate))
        elapsedDays = java.time.temporal.ChronoUnit.DAYS
            .between(createdDate, java.time.LocalDate.now(zone))
            .coerceIn(0L, totalDays)
    } else {
        totalDays = if (totalMs > 0) maxOf(1L, totalMs / 86_400_000L) else 1L
        elapsedDays = (elapsedMs / 86_400_000L).coerceIn(0L, totalDays)
    }
    val progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)

    val theme = themeFor(card.eventType, overdue)
    val typeLabel = when (card.eventType) {
        "EXAM" -> "考试"; "SUBMISSION" -> "作业/提交"; "MEETING" -> "会议"
        "INTERVIEW" -> "面试"; else -> "事项"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(theme.colors)),
    ) {
        // 右上角装饰（月亮 / 星点），模仿番茄的夜空感
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (theme.decor) {
                "moon" -> {
                    drawCircle(Color.White.copy(alpha = 0.92f), radius = h * 0.20f, center = Offset(w * 0.80f, h * 0.34f))
                    drawCircle(theme.colors.first().copy(alpha = 0.96f), radius = h * 0.175f, center = Offset(w * 0.855f, h * 0.295f))
                }
                "star" -> {
                    // 一道流星
                    drawLine(
                        Color.White.copy(alpha = 0.75f),
                        start = Offset(w * 0.72f, h * 0.14f),
                        end = Offset(w * 0.86f, h * 0.62f),
                        strokeWidth = 3f,
                    )
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = 4f, center = Offset(w * 0.72f, h * 0.14f))
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = 2.5f, center = Offset(w * 0.90f, h * 0.24f))
                    drawCircle(Color.White.copy(alpha = 0.40f), radius = 2f, center = Offset(w * 0.66f, h * 0.30f))
                }
                else -> {
                    drawCircle(Color.White.copy(alpha = 0.16f), radius = h * 0.30f, center = Offset(w * 0.86f, h * 0.24f))
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.16f, center = Offset(w * 0.70f, h * 0.44f))
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)) {
            // 第一行：标题 + 还剩 N 天
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = card.title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (overdue) "已逾期"
                        else if (isDateOnly) dateOnlyLabel(card.deadlineAt)
                        else "还剩",
                        color = theme.accent,
                        fontSize = 12.sp,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = remainValue,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " $remainUnit",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 摘要：只显示一行，补充关键细节（要带什么、在哪、什么要求）
            if (card.summary.isNotBlank()) {
                Text(
                    text = card.summary,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                )
            }

            // 进度条：直观看出时间过了多少
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.20f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.accent.copy(alpha = 0.85f)),
                )
            }

            Spacer(Modifier.height(7.dp))

            // 底行：已过 X/Y 天 + 截止日期
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已过 $elapsedDays/$totalDays 天 · $typeLabel",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDeadlineFriendly(card.deadlineAt),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ManualAddDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var deadlineText by remember { mutableStateOf("") }
    val parsed = parseDate(deadlineText)
    val deadlineError = when {
        deadlineText.isBlank() -> null
        parsed == null -> "日期格式看不懂，试试 2026-12-31 20:00"
        parsed <= System.currentTimeMillis() -> "这个时间已经过去了"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增未来事项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("事项标题") }, singleLine = true)
                OutlinedTextField(summary, { summary = it }, label = { Text("摘要/备注（选填）") }, minLines = 2)
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it },
                    label = { Text("截止时间，如 2026-12-31 20:00") },
                    singleLine = true,
                    isError = deadlineError != null,
                )
                deadlineError?.let { Text(it, color = Color(0xFFD55245), fontSize = 12.sp) }
                parsed?.takeIf { deadlineError == null }?.let {
                    Text("将提醒：${formatDeadline(it)}", color = Color(0xFF4F6BFF), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onSave(title, summary, it, inferDateOnlyFromInput(deadlineText)) } },
                enabled = title.isNotBlank() && deadlineError == null && parsed != null,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MemoryRepository(AppDatabase.get(context)) }
    val scheduler = remember { ReminderScheduler(context, repository) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // pick(选方式) | text(输入文字) | voice(录音中) | loading | done
    var stage by remember { mutableStateOf("pick") }
    var typedText by remember { mutableStateOf("") }

    // 多事项勾选：识别出多条时先让用户选，再建卡
    var candidates by remember { mutableStateOf<List<ExtractedMemory>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pendingSource by remember { mutableStateOf<SourceDraft?>(null) }

    /**
     * 建一条卡片。
     * 时间缺失/已过期时**不中断整批**（多事项勾选时可能其中一条时间已过），
     * 只把该条跳过并记下原因，由调用方汇总。
     *
     * 一次把四样东西都落库（之前都白白丢掉了）：
     *   - aiRecord：模型原始返回，存进 ai_parse_record，详情页可回看"AI 当时认出了什么"
     *   - priority：按事件类型给默认优先级（考试/面试更急）
     *   - remindersMinutes：优先用模型建议的提前量，没有才用自适应默认值
     *   - tagNames：按类型自动打标签，让 tag / memory_tag 两张表真正被用上
     */
    suspend fun saveOne(item: ExtractedMemory, source: SourceDraft?, closeDialog: Boolean) {
        val deadline = DeadlineParser.parse(item.deadline, item.timezone)
        if (deadline == null) {
            error = "「${item.title}」没有找到明确的截止时间"
            if (closeDialog) { busy = false; stage = "pick" }
            return
        }
        if (deadline <= System.currentTimeMillis()) {
            error = "「${item.title}」的时间已经过去了（${formatDeadlineFriendly(deadline)}）"
            if (closeDialog) { busy = false; stage = "pick" }
            return
        }
        val effectiveSource = source ?: SourceDraft(
            kind = "AI-TEXT", sourceUri = null, copiedFilePath = null,
            mimeType = "text/plain", byteSize = 0L, rawText = item.title,
            ocrText = null, contentHash = sha256(item.title.toByteArray(StandardCharsets.UTF_8)),
        )
        // 模型建议的提前量（已经是分钟数），非法值在 validate 里被过滤过，这里再兜一次
        val suggested = item.reminderSuggestions.map { it.offsetMinutes }.filter { it in 0..(365 * 24 * 60) }
        val eventId = repository.createImportedCountdown(
            item.title, item.summary, deadline, effectiveSource,
            aiRecord = AiRecordDraft(
                provider = "codebuddy",
                model = AiDefaults.MODEL,
                promptVersion = PROMPT_VERSION,
                resultJson = buildString {
                    append("{\"title\":").append(quoteJson(item.title))
                    append(",\"summary\":").append(quoteJson(item.summary))
                    append(",\"deadline\":").append(quoteJson(item.deadline ?: ""))
                    append(",\"eventType\":").append(quoteJson(item.eventType))
                    append(",\"location\":").append(quoteJson(item.location ?: ""))
                    append(",\"confidence\":").append(item.confidence)
                    append("}")
                },
                confidence = item.confidence,
                status = "SUCCESS",
            ),
            eventType = normalizeEventType(item.eventType),
            priority = defaultPriorityFor(item.eventType),
            remindersMinutes = suggested.ifEmpty { listOf(1440, 60) },
            dateOnly = item.dateOnly,
            tagNames = tagsFor(item),
            // AI 识别出来的地点和补充说明，之前一律被丢弃，现在存下来
            location = item.location,
            extraNote = item.notes,
        )
        repository.futureReminders().filter { it.eventId == eventId }.forEach(scheduler::schedule)
        if (closeDialog) {
            stage = "done"
            onSaved()
        }
    }

    /** 统一的建卡流程：截图、文字两种入口最后都走这里。 */
    suspend fun saveFromExtraction(
        title: String,
        summary: String,
        deadlineRaw: String?,
        timezone: String?,
        sourceKind: String,
        source: SourceDraft,
    ) {
        saveOne(
            ExtractedMemory(
                title = title, summary = summary, deadline = deadlineRaw, timezone = timezone,
            ),
            source.copy(kind = sourceKind),
            closeDialog = true,
        )
    }

    /**
     * 用当前配置构造 AI provider。
     *
     * 默认走**手机直连 CodeBuddy**（不经 NAS）。
     * 原因（实测）：经 NAS 的 Tailscale Funnel 时，TLS 握手要 3.3-7.5 秒、首字节 7-17 秒
     * （家庭出口被中继到美国旧金山，DERP 154ms 起），同一张图要 88 秒；
     * 直连只要 3.7-4.1 秒。而且直连时 reasoning:{enabled:false} 真正生效（think=0）。
     *
     * 用户在设置页填了自定义 Base URL / Key 时，改用标准 OpenAI 兼容路径。
     */
    suspend fun buildProvider(): AiProvider {
        val config = AppSettings(context).aiConfig.first()
        val customUrl = config.baseUrl.takeIf { it.isNotBlank() && it != AiDefaults.BASE_URL }
        val customKey = config.apiKey.takeIf { it.isNotBlank() && it != AiDefaults.API_KEY }
        val customModel = config.model.takeIf { it.isNotBlank() && it != AiDefaults.MODEL }
        val useCustom = customUrl != null || customKey != null || customModel != null
        if (useCustom) {
            return OpenAiCompatibleProvider(
                baseUrl = customUrl ?: AiDefaults.VISION_BASE_URL,
                apiKey = config.apiKey,
                model = customModel ?: AiDefaults.VISION_MODEL,
                stableUserId = config.stableUserId,
                disableReasoning = true,
            )
        }
        return CodeBuddyDirectProvider(
            accessToken = AiDefaults.CODEBUDDY_ACCESS_TOKEN,
            refreshToken = AiDefaults.CODEBUDDY_REFRESH_TOKEN,
            model = AiDefaults.MODEL,
        )
    }

    // 选完图先进「确认页」：图片下方直接放一个备注输入框，留空即为没有备注。
    // 之前是选完立刻发去识别，截图信息不全时用户没机会补充。
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var noteText by remember { mutableStateOf("") }

    /** 所有选图入口统一走这里。 */
    fun runImport(uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        pickedUris = uris
        error = null
        noteText = ""
        stage = "confirm"
    }

    /**
     * 用户确认后发给 AI（把所有图 + 备注一起发，备注可以是空的）。
     *
     * 改为**列出所有未来事项**让用户勾选：
     * 之前只取"最早的一个"，导致第二件事被硬塞进 summary 里当备注
     * （如"另：国家奖学金申报截止10月15日已过期"），既没建成卡片又污染摘要。
     */
    fun confirmImport() {
        if (busy || pickedUris.isEmpty()) return
        busy = true; stage = "loading"; error = null
        val uris = pickedUris
        val note = noteText.trim()
        scope.launch {
            try {
                // 每张图都可能被切成多块，全部按顺序拼在一起发给模型
                val tiles = withContext(Dispatchers.IO) { uris.flatMap { readAndCompressMulti(context, it) } }
                val dataUrls = tiles.map { OpenAiCompatibleProvider.imageDataUrl(it, "image/jpeg") }
                val baseText = if (uris.size > 1) "请识别这些截图中的事项。" else "请识别这张截图中的事项。"
                val userText = if (note.isBlank()) baseText else "$baseText\n\n用户补充说明（请一并参考）：$note"
                val provider = buildProvider()
                val found = provider.extractAllDeadlines(
                    AiInput(userText, null, nowIso(), ZoneId.systemDefault().id, imageDataUrls = dataUrls)
                ).getOrThrow()

                // 记下这批图的来源信息，供勾选后逐条建卡
                val hash = sha256((uris.joinToString("|") + "|" + note).toByteArray(StandardCharsets.UTF_8))
                pendingSource = SourceDraft(
                    kind = "AI-VISION",
                    sourceUri = uris.first().toString(),
                    copiedFilePath = java.io.File(context.filesDir, "imports/$hash.jpg").path,
                    mimeType = "image/jpeg",
                    byteSize = tiles.sumOf { it.size }.toLong(),
                    rawText = note,
                    ocrText = null,
                    contentHash = hash,
                )
                // 只有一条就不用让用户选了，直接建卡
                if (found.size == 1) {
                    val one = found.first()
                    saveFromExtraction(
                        one.title, one.summary, one.deadline, one.timezone, "AI-VISION", pendingSource!!,
                    )
                } else {
                    candidates = found
                    selected = found.indices.toSet()   // 默认全选，用户可取消
                    busy = false
                    stage = "choose"
                }
            } catch (e: Exception) {
                error = e.message ?: "识别失败，请重试"
                busy = false; stage = "confirm"
            }
        }
    }

    /** 用户在勾选页点「创建选中项」：只建勾上的那些。 */
    fun createSelected() {
        if (busy || candidates.isEmpty()) return
        val picks = candidates.filterIndexed { index, _ -> index in selected }
        if (picks.isEmpty()) {
            error = "至少选一个，或点「都不要」返回"
            return
        }
        busy = true; stage = "loading"; error = null
        val source = pendingSource
        scope.launch {
            var made = 0
            var lastError: String? = null
            for (item in picks) {
                try {
                    saveOne(item, source, closeDialog = false)
                    made++
                } catch (e: Exception) {
                    lastError = e.message
                }
            }
            busy = false
            if (made > 0) {
                stage = "done"
                candidates = emptyList()
                selected = emptySet()
            } else {
                error = lastError ?: "创建失败，请重试"
                stage = "choose"
            }
        }
    }

    /** 文字/语音统一入口：把一段自然语言交给 AI 解析。 */
    fun runText(text: String, kind: String) {
        if (busy || text.isBlank()) return
        busy = true; stage = "loading"; error = null
        scope.launch {
            try {
                val provider = buildProvider()
                val extracted = provider.extractDeadline(
                    AiInput(text, null, nowIso(), ZoneId.systemDefault().id)
                ).getOrThrow()
                saveFromExtraction(
                    extracted.title, extracted.summary, extracted.deadline, extracted.timezone,
                    kind,
                    SourceDraft(
                        kind = kind,
                        sourceUri = null,
                        copiedFilePath = null,
                        mimeType = "text/plain",
                        byteSize = text.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                        rawText = text,
                        ocrText = null,
                        contentHash = sha256(text.toByteArray(StandardCharsets.UTF_8)),
                    ),
                )
            } catch (e: Exception) {
                error = e.message ?: "识别失败，请重试"
                busy = false; stage = "pick"
            }
        }
    }

    // Android 13+ 用 PickMultipleVisualMedia 可一次选多张；低版本用 OpenDocument 的 EXTRA_ALLOW_MULTIPLE。
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) runImport(uris)
    }
    val legacyPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { runImport(listOf(it)) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF4F6BFF))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (stage) {
                        "loading" -> "AI 正在识别…"
                        "done" -> "已添加！"
                        "text" -> "说说是什么事"
                        "confirm" -> "确认一下"
                        "choose" -> "要建哪几个？"
                        else -> "AI 智能识别"
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (stage) {
                    "pick" -> {
                        Text(
                            "选截图，或直接打字让 AI 理解",
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else legacyPicker.launch(arrayOf("image/*"))
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("选择截图")
                        }
                        OutlinedButton(
                            onClick = { stage = "text"; typedText = "" },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("打字输入")
                        }
                    }
                    "text" -> {
                        Text(
                            "提示：输入法键盘上的麦克风按钮也能说话转文字",
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(stage) {
                            if (stage == "text") runCatching { focusRequester.requestFocus() }
                        }
                        OutlinedTextField(
                            value = typedText,
                            onValueChange = { typedText = it },
                            label = { Text("例如：下周三下午2点交数据库实验报告，要交纸质版") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                        Button(
                            onClick = { runText(typedText, "AI-TEXT") },
                            enabled = typedText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("让 AI 识别") }
                    }
                    "confirm" -> {
                        // 图片下方直接一个备注框，留空就代表没有备注。
                        Text(
                            "已选 ${pickedUris.size} 张截图",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("备注（可不填）", fontSize = 14.sp) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "loading" -> {
                        androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF4F6BFF))
                        Text("AI 正在理解…", color = Color.Gray)
                    }
                    "choose" -> {
                        // 识别出多条时，让用户挑要建哪些
                        Text(
                            "找到 ${candidates.size} 个事项，选要建的：",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        candidates.forEachIndexed { index, item ->
                            val on = index in selected
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (on) Color(0xFFEEF1FF) else Color(0xFFF7F8FA),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + index else selected - index
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val due = DeadlineParser.parse(item.deadline, item.timezone)
                                    Text(
                                        if (due == null) "时间待定" else formatDeadlineFriendly(due),
                                        color = if (due == null) Color(0xFFD55245) else Color.Gray,
                                        fontSize = 12.sp,
                                    )
                                    if (item.summary.isNotBlank()) {
                                        Text(
                                            item.summary,
                                            color = Color(0xFF7C8495),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "done" -> {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                        Text("识别完成，倒计时已自动创建！", fontWeight = FontWeight.Bold)
                    }
                }
                error?.let {
                    Text(it, color = Color(0xFFD55245), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        },
        confirmButton = {
            when (stage) {
                "done" -> Button(onClick = onDismiss) { Text("完成") }
                "text" -> TextButton(onClick = { runText(typedText, "AI-TEXT") }, enabled = typedText.isNotBlank()) { Text("识别") }
                "confirm" -> Button(
                    onClick = { confirmImport() },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                ) { Text("开始识别") }
                "choose" -> Button(
                    onClick = { createSelected() },
                    enabled = !busy && selected.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (selected.isEmpty()) "选一个" else "创建 ${selected.size} 个") }
                else -> {}
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(onClick = {
                    when (stage) {
                        "text" -> { stage = "pick"; error = null }
                        "confirm" -> { stage = "pick"; error = null; pickedUris = emptyList() }
                        "choose" -> {
                            // 「都不要」：直接关掉，什么都不建
                            candidates = emptyList(); selected = emptySet(); pendingSource = null
                            onDismiss()
                        }
                        else -> onDismiss()
                    }
                }) {
                    Text(
                        when (stage) {
                            "text", "confirm" -> "返回"
                            "choose" -> "都不要"
                            else -> "取消"
                        }
                    )
                }
            }
        },
    )
}

/**
 * 事项详情 —— 仿番茄 ToDo：一张中等大小的深色卡。
 *
 * 结构（自上而下）：
 *   工具栏（左：关闭；右：编辑/删除）—— 一行内左右分开，不重叠
 *   回显条：标题 + 截止时间 + 还有 N 天
 *   备注（主题下面的具体内容）
 *   已完成按钮
 *   建立时间（底部小字）
 *
 * 相比上一版去掉了：中间那行重复的大标题（和回显条重复）、
 * 过高的卡片（从 88% 高改成自适应，内容多少就多高）。
 */
/**
 * 「补充信息」对话框 —— 给已有卡片追加截图更新。
 *
 * 场景：通知先说「10月23日晚7点 地点30107」建了卡，群里又发「改到晚上8点」。
 * 用户从卡片详情页点进来，选截图或打字，AI 提取变更内容，合并进原卡。
 *
 * 流程：选截图 → AI 识别 → 展示「将要改什么」→ 确认 → 合并
 */
@Composable
private fun SupplementDialog(
    card: CountdownCard,
    onDismiss: () -> Unit,
    onApplied: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MemoryRepository(AppDatabase.get(context)) }
    val scheduler = remember { ReminderScheduler(context, repository) }

    var stage by remember { mutableStateOf("pick") }
    var typedText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var extracted by remember { mutableStateOf<ExtractedMemory?>(null) }
    var sourceText by remember { mutableStateOf<String?>(null) }
    var rawJson by remember { mutableStateOf<String?>(null) }
    var changes by remember { mutableStateOf<List<String>>(emptyList()) }

    // 与主识别流程共用同一套 provider 构造（AI 凭据来自 BuildConfig）
    fun localProvider(): CodeBuddyDirectProvider = CodeBuddyDirectProvider(
        accessToken = AiDefaults.CODEBUDDY_ACCESS_TOKEN,
        refreshToken = AiDefaults.CODEBUDDY_REFRESH_TOKEN,
        model = AiDefaults.MODEL,
    )

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris ->
        if (uris.isNotEmpty()) {
            stage = "loading"
            error = null
            scope.launch {
                runCatching {
                    // 复用主流程的图片处理：切成多块 + 压缩 + 转 data URL
                    val tiles = withContext(Dispatchers.IO) {
                        uris.flatMap { readAndCompressMulti(context, it) }
                    }
                    val dataUrls = tiles.map { OpenAiCompatibleProvider.imageDataUrl(it, "image/jpeg") }
                    if (dataUrls.isEmpty()) throw IllegalStateException("图片读取失败")

                    val provider = localProvider()
                    val first = provider.extractSupplement(
                        AiInput(
                            "这是对已有事项的补充信息，请提取其中的变更内容（新时间、新地点、附加说明）。",
                            null,
                            nowIso(),
                            ZoneId.systemDefault().id,
                            imageDataUrls = dataUrls,
                        ),
                    ).getOrThrow() ?: throw IllegalStateException("没识别出任何内容")

                    extracted = first
                    rawJson = first.toString()
                    sourceText = "补充截图：" + uris.size + " 张"
                    stage = "preview"
                }.onFailure {
                    error = it.message ?: "识别失败"
                    stage = "pick"
                }
            }
        }
    }

    fun recognizeText() {
        if (typedText.isBlank()) return
        stage = "loading"
        error = null
        scope.launch {
            runCatching {
                val first = localProvider().extractSupplement(
                    AiInput(
                        "这是对已有事项的补充信息，请提取其中的变更内容：\n" + typedText,
                        null,
                        nowIso(),
                        ZoneId.systemDefault().id,
                    ),
                ).getOrThrow() ?: throw IllegalStateException("没识别出任何内容")
                extracted = first
                rawJson = first.toString()
                sourceText = typedText
                stage = "preview"
            }.onFailure {
                error = it.message ?: "识别失败"
                stage = "pick"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补充信息", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "给「" + card.title + "」补充",
                    fontSize = 12.sp,
                    color = Color(0xFF5A6376),
                )

                when (stage) {
                    "pick" -> {
                        Text(
                            "再提交一次截图或输入文字，AI 会提取变更内容并合并进来。",
                            fontSize = 13.sp,
                            color = Color.Gray,
                        )

                        Button(
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("选择截图")
                        }

                        OutlinedTextField(
                            value = typedText,
                            onValueChange = { typedText = it },
                            label = { Text("或者直接打字（如：改到晚上8点）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                        )

                        if (typedText.isNotBlank()) {
                            Button(
                                onClick = { recognizeText() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("识别这段文字") }
                        }
                    }

                    "loading" -> {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("AI 正在识别…", fontSize = 13.sp, color = Color.Gray)
                        }
                    }

                    "preview" -> {
                        val ex = extracted
                        if (ex != null) {
                            Text("识别结果", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            val newDl = DeadlineParser.parse(ex.deadline, ex.timezone)
                            val timeChanged = newDl != null && newDl != card.deadlineAt
                            val locChanged = !ex.location.isNullOrBlank() && ex.location != card.location
                            val noteNew = ex.notes?.takeIf { it.isNotBlank() }

                            if (!timeChanged && !locChanged && noteNew == null) {
                                Text(
                                    "没发现新信息。识别到的和现有内容一致。",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD55245),
                                )
                            } else {
                                if (timeChanged && newDl != null) {
                                    Text(
                                        "时间：" + formatDeadlineFriendly(card.deadlineAt) +
                                            " → " + formatDeadlineFriendly(newDl),
                                        fontSize = 13.sp,
                                        color = Color(0xFF4F6BFF),
                                    )
                                }
                                if (locChanged) {
                                    Text(
                                        "地点：" + (card.location ?: "（无）") + " → " + ex.location,
                                        fontSize = 13.sp,
                                        color = Color(0xFF4F6BFF),
                                    )
                                }
                                if (noteNew != null) {
                                    Text(
                                        "补充：" + noteNew,
                                        fontSize = 13.sp,
                                        color = Color(0xFF4F6BFF),
                                    )
                                }
                            }
                        }
                    }

                    "done" -> {
                        Text("已合并", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E9184))
                        changes.forEach { c ->
                            Text("· " + c, fontSize = 13.sp)
                        }
                    }
                }

                error?.let {
                    Text(it, color = Color(0xFFD55245), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            when (stage) {
                "preview" -> {
                    Button(
                        onClick = {
                            val ex = extracted ?: return@Button
                            scope.launch {
                                val applied = runCatching {
                                    repository.applySupplement(
                                        eventId = card.eventId,
                                        newDeadline = DeadlineParser.parse(ex.deadline, ex.timezone),
                                        newLocation = ex.location,
                                        newNote = ex.notes,
                                        newSummary = ex.summary,
                                        sourceText = sourceText,
                                        rawResultJson = rawJson,
                                        dateOnly = ex.dateOnly,
                                    )
                                }.getOrElse {
                                    error = it.message ?: "合并失败"
                                    emptyList()
                                }
                                if (applied.isNotEmpty()) {
                                    scheduler.rescheduleAll()
                                    changes = applied
                                    stage = "done"
                                    onApplied(applied)
                                } else if (error == null) {
                                    error = "没有需要更新的内容"
                                }
                            }
                        },
                    ) { Text("确认合并") }
                }
                "done" -> {
                    Button(onClick = onDismiss) { Text("好") }
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (stage != "loading") {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun DetailDialog(
    card: CountdownCard,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSupplement: () -> Unit,
) {
    val theme = themeFor(card.eventType, card.status == "OVERDUE")
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(Brush.linearGradient(theme.colors), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {},
            ) {
                // ── 工具栏：左关闭、右三个操作，两端分开不重叠 ──
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailIconButton(Icons.Default.Close, "关闭", onDismiss)
                    Spacer(Modifier.weight(1f))
                    // 补充信息：再提交一次截图，把变更合并进这张卡
                    DetailIconButton(Icons.Default.AddCircle, "补充信息", onSupplement)
                    Spacer(Modifier.width(4.dp))
                    DetailIconButton(Icons.Default.Edit, "编辑", onEdit)
                    Spacer(Modifier.width(4.dp))
                    DetailIconButton(Icons.Default.Delete, "删除", onDelete)
                }

                // ── 回显条：标题 + 截止 + 还有 N 天 ──
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatDeadlineFriendly(card.deadlineAt),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    // 详情页也跟时间走（否则打开着不动，倒计时就一直是旧值）
    val detailTick = rememberSecondTick()
    val remain = if (card.isDateOnly()) {
        formatRemainingDateOnly(card.deadlineAt)
    } else {
        formatRemaining(card.deadlineAt - detailTick)
    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("还有", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(remain.first, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(3.dp))
                            Text(remain.second, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    }
                }

                // ── 备注：主题下面的具体内容 ──
                if (card.summary.isNotBlank()) {
                    Text(
                        card.summary,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                // ── 地点：AI 从截图里识别出来的，以前会被丢掉 ──
                card.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Place, null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            loc,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                        )
                    }
                }

                // ── 补充说明：范围限定、弹性时间等散装信息 ──
                card.extraNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp).padding(top = 1.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            note,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                // ── 已完成按钮 ──
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.94f)),
                ) {
                    Icon(Icons.Default.Check, null, tint = Color(0xFF2E3A8C), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("已完成", color = Color(0xFF2E3A8C), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // ── 建立时间（底部小字，学番茄）──
                Text(
                    "建立于 ${formatCreatedAt(card.createdAt)}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/** 建立时间：日期格式（卡片没有 createdAt 时退回截止时间所在日期）。 */
private fun formatCreatedAt(createdAt: Long): String {
    val millis = if (createdAt > 0) createdAt else System.currentTimeMillis()
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
}

/**
 * 详情卡顶部的图标按钮。
 *
 * 不加圆形底色：半透明白底叠在深色渐变卡上会糊成一团阴影，
 * 看起来又脏又挡字。纯白图标直接放在卡上才干净（番茄也是这么做的）。
 */
@Composable
private fun DetailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

/** 提前量说成人话。 */

/** 提前量说成人话。 */

/** 提前量说成人话。 */

/** 提前量说成人话。 */
private fun formatOffset(minutes: Int): String = when {
    minutes < 60 -> "${minutes} 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    minutes % 1440 == 0 -> "${minutes / 1440} 天"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}

/**
 * 编辑事项（仿番茄的编辑弹窗）：标题/备注/截止时间都能改。
 * 时间用宽容解析器 —— 手输「2026-10-18 12:00」甚至全角都能认。
 */
@Composable
private fun EditDialog(
    card: CountdownCard,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(card.title) }
    var summary by remember { mutableStateOf(card.summary) }
    // 纯日期卡片编辑时只显示日期，不要那个假钟点（23:59 是占位值）
    val cardIsDateOnly = card.isDateOnly()
    var deadlineText by remember {
        mutableStateOf(
            if (cardIsDateOnly) {
                java.time.Instant.ofEpochMilli(card.deadlineAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } else {
                formatDeadline(card.deadlineAt)
            },
        )
    }
    val parsed = parseDate(deadlineText)
    val deadlineError = when {
        deadlineText.isBlank() -> null
        parsed == null -> "日期格式看不懂，试试 2026-12-31 20:00"
        // 允许改到过去的时间吗？不允许：改完立刻就逾期没意义。
        // 但注意：如果卡片本身已逾期，原时间也会被判"已过去"，这里按"必须是未来"处理。
        parsed <= System.currentTimeMillis() -> "截止时间必须晚于现在"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑事项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
                OutlinedTextField(summary, { summary = it }, label = { Text("备注（可不填）") }, minLines = 2)
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it },
                    label = { Text("截止时间，如 2026-12-31 20:00") },
                    singleLine = true,
                    isError = deadlineError != null,
                )
                deadlineError?.let { Text(it, color = Color(0xFFD55245), fontSize = 12.sp) }
                parsed?.takeIf { deadlineError == null }?.let {
                    Text("修改后提醒：${formatDeadlineFriendly(it)}（会自动重排）", color = Color(0xFF4F6BFF), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onSave(title, summary, it, inferDateOnlyFromInput(deadlineText)) } },
                enabled = title.isNotBlank() && deadlineError == null && parsed != null,
            ) { Text("保存修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 事项状态 → 中文。数据库里存的是英文枚举。 */
private fun statusLabel(status: String): String = when (status) {
    "UPCOMING" -> "进行中"
    "COMPLETED" -> "已完成"
    "CANCELLED" -> "已删除"
    "OVERDUE" -> "已逾期"
    else -> status
}

/** 事项类型 → 中文。 */
private fun typeLabel(eventType: String): String = when (eventType) {
    "EXAM" -> "考试"
    "SUBMISSION" -> "作业提交"
    "MEETING" -> "会议"
    "INTERVIEW" -> "面试"
    "OTHER" -> "其他"
    else -> eventType
}

@Composable
private fun StatisticsPage(
    padding: PaddingValues,
    statuses: List<com.kyf778.yishi.data.StatusCount>,
    types: List<com.kyf778.yishi.data.TypeCount>,
    pastCards: List<CountdownCard>,
    onRestore: (CountdownCard) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("事项状态分布", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (statuses.isEmpty()) {
            item { Text("还没有事项", color = Color.Gray, fontSize = 13.sp) }
        } else {
            items(statuses) {
                Text(
                    "${statusLabel(it.status)}：${it.count} 条",
                    modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).fillMaxWidth().padding(14.dp),
                )
            }
        }
        item { Text("事项类型统计", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp)) }
        if (types.isEmpty()) {
            item { Text("还没有事项", color = Color.Gray, fontSize = 13.sp) }
        } else {
            items(types) {
                Text(
                    "${typeLabel(it.eventType)}：${it.count} 条",
                    modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).fillMaxWidth().padding(14.dp),
                )
            }
        }

        // 历史：已完成的事项不消失，放这里随时回看（可恢复）
        item {
            Text(
                "已完成（${pastCards.size} 条）",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        if (pastCards.isEmpty()) {
            item { Text("还没有已完成的事项", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        } else {
            items(pastCards, key = { "past-${it.eventId}" }) { card ->
                val done = card.status == "COMPLETED"
                Column(
                    Modifier.fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            card.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (done) "已完成" else "已删除",
                            color = if (done) Color(0xFF2E9184) else Color(0xFF7C8495),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        // 恢复：把事项放回倒计时列表
                        TextButton(
                            onClick = { onRestore(card) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("恢复", fontSize = 12.sp, color = Color(0xFF4F6BFF)) }
                    }
                    Text(
                        "截止 ${formatDeadlineFriendly(card.deadlineAt)}",
                        color = Color(0xFF7C8495),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (card.summary.isNotBlank()) {
                        Text(card.summary, color = Color.Gray, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

/**
 * 提醒记录页：回答「提醒到底有没有弹出来」。
 *
 * 系统通知一旦划过就找不回来了，所以每次触发都在 notification_log 里落一条，
 * 这里按时间倒序列出，包含成功/被拒/失败三种结果。
 */
@Composable
private fun NotificationLogPage(padding: PaddingValues, logs: List<com.kyf778.yishi.data.NotificationLogRow>) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Notifications, null, tint = Color(0xFFB9C0D0), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("还没有提醒记录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "等提醒触发后，这里会记下时间和结果",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "共 ${logs.size} 条提醒记录（最近的在上面）",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(logs, key = { it.id }) { row ->
            val (label, color) = when (row.result) {
                "SHOWN" -> "已提醒" to Color(0xFF2E9184)
                "SKIPPED_NO_PERMISSION" -> "未提醒（缺权限）" to Color(0xFFC07A3E)
                else -> "提醒失败" to Color(0xFFD55245)
            }
            Column(
                Modifier.fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.eventTitle?.takeIf { it.isNotBlank() } ?: "（事项已删除）",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "触发时间：${row.firedAt?.let { formatDeadlineFriendly(it) } ?: "—"}",
                    color = Color(0xFF7C8495),
                    fontSize = 12.sp,
                )
                if (row.scheduledAt > 0) {
                    Text(
                        "计划时间：${formatDeadlineFriendly(row.scheduledAt)}",
                        color = Color(0xFF7C8495),
                        fontSize = 12.sp,
                    )
                }
                row.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFD55245), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    val config by settings.aiConfig.collectAsStateWithLifecycle(AiConfig("", "", "", ""))
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 与提醒设置") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // ── 提醒可靠性：有问题的项才显示（像其它 App 一样，正常时保持安静）──
                ReminderHealthCheck(context)

                Text(
                    "AI 默认已内置手机直连（CodeBuddy），不用填任何东西；" +
                        "只有你想换成别的 AI 服务时才需要填下面这些。",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL（留空即用内置直连）") }, singleLine = true)
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key（留空即用内置）") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("模型 ID（内置 ${AiDefaults.MODEL}）") }, singleLine = true)
                // 让用户能立刻看到"提醒到点时长什么样"，不用等真到点
                TextButton(onClick = { sendTestNotification(context) }) {
                    Text("发送一条测试通知（看看提醒长什么样）")
                }
            }
        },
        confirmButton = { Button(onClick = { scope.launch { settings.saveAiConfig(AiConfig(baseUrl, apiKey, model, config.stableUserId)); onDismiss() } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 手动输入截止时间。用和 AI 识别同一套宽容解析器，
 * 这样用户写「2026/12/12 9:00」「2026年12月12日」甚至全角「2026－12－12 09：00」都能识别。
 */
private fun parseDate(value: String): Long? = DeadlineParser.parse(value, ZoneId.systemDefault().id)
private fun formatDeadline(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun nowIso(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(java.time.OffsetDateTime.now())

private fun readAndCompress(context: android.content.Context, uri: Uri): ByteArray = readAndCompressMulti(context, uri).first()

/**
 * 把截图切成适合模型识别的若干张。
 *
 * 关键点（实测得出）：
 * 1. 不能按"最长边"整体缩放。1080x5000 的聊天截图按最长边缩到 2400 后宽度只剩 518px，
 *    1080x10000 更是只剩 259px，中文字全糊，模型会返回 deadline=null。
 * 2. 正确做法是【宽度优先】：先把宽度缩到上限（文字保持清晰），
 *    再沿纵向切块，每块高度<=1400 且带 120px 重叠，避免把一句话切两半。
 * 3. 宽度和画质决定【上传体积】，而上传体积决定等待时间——
 *    公网入口（Tailscale Funnel）上行只有约 10-14 KB/s，
 *    实测 1600 宽/q82 的 3 块图上行 531KB 要等 70 秒，
 *    改成 1080 宽/q78 后 307KB 只要 31 秒，识别结果完全一致。
 */
private fun readAndCompressMulti(context: android.content.Context, uri: Uri, maxTiles: Int = 8): List<ByteArray> {
    val resolver = context.contentResolver
    val copied = java.io.File(context.filesDir, "imports/${sha256(uri.toString().toByteArray())}.jpg")
    copied.parentFile?.mkdirs()
    if (!copied.exists()) resolver.openInputStream(uri)?.use { input -> copied.outputStream().use { output -> input.copyTo(output, 8192) } }

    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(copied.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片尺寸" }

    // 按宽度取样：目标是宽度不超过 maxWidth，所以采样率以宽度为准
    val maxWidth = MAX_IMAGE_WIDTH
    val sample = maxOf(1, bounds.outWidth / maxWidth)
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = android.graphics.BitmapFactory.decodeFile(copied.path, options) ?: error("无法读取图片")

    // 宽度优先缩放：只压宽度到<=maxWidth，高度按比例保留
    val decodeScale = minOf(1f, maxWidth.toFloat() / bitmap.width)
    val scaled = if (decodeScale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            bitmap, maxWidth, maxOf(1, (bitmap.height * decodeScale).toInt()), true
        )
    } else bitmap

    // 高度不超长 -> 整张发送；否则纵向切块
    val tileHeight = 1400
    if (scaled.height <= tileHeight * 3 / 2) {
        return listOf(compressJpeg(scaled))
    }
    val overlap = 120
    val tiles = mutableListOf<ByteArray>()
    var y = 0
    while (y < scaled.height && tiles.size < maxTiles) {
        val bottom = minOf(y + tileHeight, scaled.height)
        val tile = android.graphics.Bitmap.createBitmap(scaled, 0, y, scaled.width, bottom - y)
        tiles += compressJpeg(tile)
        if (bottom >= scaled.height) break
        y = bottom - overlap
    }
    return tiles.ifEmpty { listOf(compressJpeg(scaled)) }
}

private fun compressJpeg(bitmap: android.graphics.Bitmap): ByteArray =
    ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()

/**
 * 上传体积直接决定等待时间：公网入口上行只有约 10-14 KB/s。
 * 实测（同一张 QQ 长截图、同一提示词、deepseek-v4.1-flash）：
 *   1600 宽/q82 → 上行 531KB → 70.1s
 *   1080 宽/q78 → 上行 307KB → 31.4s   ← 采用，识别结果与上面完全一致
 *   900 宽/q75  → 上行 225KB → 27.3s   （再快 4 秒，但小字更险）
 */
private const val MAX_IMAGE_WIDTH = 1080
private const val JPEG_QUALITY = 78

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * 立刻弹一条示例通知，让用户确认"提醒到点时长什么样"（也能验证华为手表是否收到）。
 * 用和真实提醒完全相同的渠道与样式，只是内容固定。
 */
private fun sendTestNotification(context: android.content.Context) {
    val manager = androidx.core.app.NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    com.kyf778.yishi.reminder.ReminderReceiver.createChannels(context)

    val open = android.app.PendingIntent.getActivity(
        context,
        9901,
        Intent(context, MainActivity::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = androidx.core.app.NotificationCompat.Builder(
        context,
        com.kyf778.yishi.reminder.ReminderReceiver.CHANNEL_URGENT,
    )
        .setSmallIcon(R.drawable.ic_stat_yishi)
        .setContentTitle("数据库设计验收 + 交实验报告")
        .setContentText("还有 30 分钟截止 · 今天 16:00")
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(open)
        .setStyle(
            androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText("还有 30 分钟截止 · 今天 16:00\n需提交实验报告，地点 A301（这是一条测试通知）"),
        )
        // 真实提醒里的三个按钮（完成/稍后/打开）需要真实的 eventId/reminderId 才能生效，
        // 不加操作按钮：和真实提醒保持一致（真实提醒只有点通知本体进 App）
        .setVibrate(longArrayOf(0, 300, 200, 300))
        .build()
    manager.notify(9901, notification)
}

/**
 * 提醒可靠性自检：这三项任一不满足，到点就可能不响或延迟。
 *
 * 1. 通知权限（Android 13+ 需要运行时授权）
 * 2. 精确闹钟（Android 12+ 默认关闭，关了会被系统攒到"下次唤醒"才响，可能延迟几十分钟）
 * 3. 电池优化白名单（国产 ROM 会冻结后台，AlarmManager 直接被拦）
 *
 * 每次进入设置页都重新检查（用 remember + 刷新），所以用户改完回来能看到变绿。
 */
@Composable
private fun ReminderHealthCheck(context: android.content.Context) {
    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 从系统设置页返回时重新检查
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifyOk = remember(refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(refresh) {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 全部正常时整块不渲染（像其它 App：没问题就不打扰）
    if (notifyOk && batteryOk) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("提醒需要修复", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        HealthRow(
            ok = notifyOk,
            label = "通知权限",
            hint = "没开就完全收不到提醒",
            actionText = if (notifyOk) null else "去开启",
            onAction = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        )
        HealthRow(
            ok = batteryOk,
            label = "电池优化白名单",
            hint = "小米等机型会冻结后台，导致提醒不响",
            actionText = if (batteryOk) null else "去设置",
            onAction = {
                // 各家 ROM 的设置入口不统一，先试标准入口，失败再打开应用详情页
                val opened = runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(android.net.Uri.parse("package:${context.packageName}")),
                    )
                }.isSuccess
                if (!opened) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun HealthRow(
    ok: Boolean,
    label: String,
    hint: String,
    actionText: String?,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (ok) "✅" else "⚠️",
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!ok) Text(hint, fontSize = 11.sp, color = Color(0xFFD55245))
        }
        if (!ok && actionText != null) {
            TextButton(onClick = onAction) { Text(actionText, fontSize = 13.sp) }
        }
    }
}





/**
 * 事件类型 → 默认优先级。
 * 考试/面试最急（1），提交/会议次之（2），其它（3）。用户之后可以在详情页改。
 */
private fun defaultPriorityFor(rawType: String): Int = when (normalizeEventType(rawType)) {
    "EXAM", "INTERVIEW" -> 1
    "SUBMISSION", "MEETING" -> 2
    else -> 3
}

/**
 * 把模型返回的类型归一到数据库约定的枚举。
 * 模型有时返回中文（"会议"、"作业提交"）或自由词（"申请提交"），
 * 直接存会让统计页出现一堆杂项，所以这里做一次映射。
 */
private fun normalizeEventType(rawType: String): String {
    val t = rawType.trim()
    if (t.isEmpty()) return "OTHER"
    val upper = t.uppercase()
    if (upper in setOf("EXAM", "SUBMISSION", "MEETING", "INTERVIEW", "OTHER")) return upper
    return when {
        t.contains("考") || t.contains("试") || t.contains("exam", true) -> "EXAM"
        t.contains("面试") || t.contains("interview", true) -> "INTERVIEW"
        t.contains("会议") || t.contains("开班会") || t.contains("meeting", true) -> "MEETING"
        t.contains("提交") || t.contains("作业") || t.contains("报告") ||
            t.contains("申请") || t.contains("申报") || t.contains("交") -> "SUBMISSION"
        else -> "OTHER"
    }
}

/**
 * 给事项自动打标签：类型中文名 + 从标题/摘要里提炼的校园关键词。
 * 这样首页能按标签筛选，tag / memory_tag 两张表也真正有数据。
 */
private fun tagsFor(item: ExtractedMemory): List<String> {
    val out = mutableListOf<String>()
    val typeTag = when (normalizeEventType(item.eventType)) {
        "EXAM" -> "考试"
        "SUBMISSION" -> "作业"
        "MEETING" -> "会议"
        "INTERVIEW" -> "面试"
        else -> null
    }
    if (typeTag != null) out += typeTag

    val haystack = "${item.title} ${item.summary}"
    val keywords = listOf(
        "奖学金", "班会", "实验", "报告", "论文", "答辩", "考试", "补考", "重修",
        "选课", "缴费", "学费", "医保", "体检", "疫苗", "实习", "竞赛", "比赛",
        "讲座", "志愿", "工时", "社团", "宿舍", "快递", "预订", "还款",
    )
    keywords.filter { haystack.contains(it) }.take(3).forEach { if (it !in out) out += it }
    return out.distinct().take(4)
}

/** 提示词版本号，写进 ai_parse_record 便于追溯"这条是用哪版提示词识别的"。 */
private const val PROMPT_VERSION = "v0.11-multi"

/** 把一个字符串转成合法的 JSON 字符串字面量（转义引号和反斜杠）。 */
private fun quoteJson(value: String): String {
    val sb = StringBuilder("\"")
    value.forEach { ch ->
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
        }
    }
    return sb.append("\"").toString()
}
