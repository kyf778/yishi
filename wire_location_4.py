"""详情页显示地点和补充说明。"""
import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"yishi\app\src\main\java\com\kyf778\yishi\MainActivity.kt"
t = open(p, encoding="utf-8").read()

# ── 详情页：在备注后面插入「地点」和「补充说明」 ──
old = """                // ── 备注：主题下面的具体内容 ──
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
                }"""

new = """                // ── 备注：主题下面的具体内容 ──
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
                }"""

if old in t:
    t = t.replace(old, new, 1)
    print("✅ 详情页已加地点和补充说明")
else:
    print("⚠️ 详情页锚点未匹配")

open(p, "w", encoding="utf-8", newline="\n").write(t)

# 检查需要的 icon import
for icon in ["Place", "Info"]:
    if f"import androidx.compose.material.icons.filled.{icon}" not in t:
        print(f"  ⚠️ 需要补 import: {icon}")
    else:
        print(f"  ✅ {icon} 已导入")
