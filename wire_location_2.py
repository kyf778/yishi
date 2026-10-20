"""Repository：建卡时接收并保存 location / extraNote。"""
import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"yishi\app\src\main\java\com\kyf778\yishi\data\Repository.kt"
t = open(p, encoding="utf-8").read()

# 1) 签名加两个参数
old_sig = """        tagNames: List<String> = emptyList(),
        dateOnly: Boolean = false,
    ): Long {
        require(title.isNotBlank()) { "标题不能为空" }
        require(deadlineAt > System.currentTimeMillis()) { "截止时间必须晚于当前时间" }
        require(priority in 1..3) { "优先级必须为 1~3" }
        require(source.contentHash.isNotBlank()) { "来源内容校验值不能为空" }"""
new_sig = """        tagNames: List<String> = emptyList(),
        dateOnly: Boolean = false,
        location: String? = null,
        extraNote: String? = null,
    ): Long {
        require(title.isNotBlank()) { "标题不能为空" }
        require(deadlineAt > System.currentTimeMillis()) { "截止时间必须晚于当前时间" }
        require(priority in 1..3) { "优先级必须为 1~3" }
        require(source.contentHash.isNotBlank()) { "来源内容校验值不能为空" }"""

if old_sig in t:
    t = t.replace(old_sig, new_sig, 1)
    print("✅ createImportedCountdown 签名已加 location/extraNote")
else:
    print("⚠️ 签名锚点未匹配")

# 2) insertEvent 处写入
old_ins = """                    eventType = eventType,
                    priority = priority,
                    dateOnly = dateOnly,
                    createdAt = now,
                ),
            )"""
new_ins = """                    eventType = eventType,
                    priority = priority,
                    dateOnly = dateOnly,
                    location = location?.trim()?.takeIf { it.isNotBlank() },
                    extraNote = extraNote?.trim()?.takeIf { it.isNotBlank() },
                    createdAt = now,
                ),
            )"""

if old_ins in t:
    t = t.replace(old_ins, new_ins, 1)
    print("✅ insertEvent 已写入 location/extraNote")
else:
    print("⚠️ insertEvent 锚点未匹配")

open(p, "w", encoding="utf-8", newline="\n").write(t)
print(f"\n检查: location= {t.count('location = location')} 处, extraNote= {t.count('extraNote = extraNote')} 处")
