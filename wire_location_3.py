"""UI 建卡时把 location 和 notes 传下去。"""
import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"yishi\app\src\main\java\com\kyf778\yishi\MainActivity.kt"
t = open(p, encoding="utf-8").read()

# ── 1) 建卡调用加参数 ──
old = """            remindersMinutes = suggested.ifEmpty { listOf(1440, 60) },
            dateOnly = item.dateOnly,
            tagNames = tagsFor(item),
        )"""
new = """            remindersMinutes = suggested.ifEmpty { listOf(1440, 60) },
            dateOnly = item.dateOnly,
            tagNames = tagsFor(item),
            // AI 识别出来的地点和补充说明，之前一律被丢弃，现在存下来
            location = item.location,
            extraNote = item.notes,
        )"""

if old in t:
    t = t.replace(old, new, 1)
    print("✅ 建卡调用已传 location / notes")
else:
    print("⚠️ 建卡调用锚点未匹配")

# ── 2) 详情页显示地点和补充说明 ──
# 找详情页显示 summary 的位置
old_detail = """                // ── 备注：主题下面的具体内容 ──"""
if old_detail in t:
    print("✅ 找到详情页备注区（稍后单独改）")

open(p, "w", encoding="utf-8", newline="\n").write(t)
print(f"\n检查: location= {'location = item.location' in t}")
