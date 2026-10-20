"""修复被破坏的 icon import。"""
import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"yishi\app\src\main\java\com\kyf778\yishi\MainActivity.kt"
t = open(p, encoding="utf-8").read()

# 找到被破坏的区域，整体替换
old = """import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaceCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked"""

new = """import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked"""

if old in t:
    t = t.replace(old, new, 1)
    print("✅ icon import 已修复")
else:
    print("⚠️ 锚点未匹配，尝试逐行清理")
    # 兜底：删除重复的 Info/Place，把 PlaceCircle 改成 CheckCircle
    lines = t.split("\n")
    seen = set()
    out = []
    for line in lines:
        if line.startswith("import androidx.compose.material.icons.filled."):
            name = line.split(".")[-1].strip()
            if name in seen:
                continue
            seen.add(name)
            if name == "PlaceCircle":
                line = "import androidx.compose.material.icons.filled.CheckCircle"
                if "CheckCircle" not in seen:
                    seen.add("CheckCircle")
                else:
                    continue
        out.append(line)
    t = "\n".join(out)
    print("✅ 已去重并修正")

open(p, "w", encoding="utf-8", newline="\n").write(t)

print("\n=== 修复后的 icon import ===")
for i, line in enumerate(t.split("\n"), 1):
    if line.startswith("import androidx.compose.material.icons.filled."):
        print(f"  L{i}: {line.strip()}")
