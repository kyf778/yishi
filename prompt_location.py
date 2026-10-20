"""给提示词补上 location / notes 的提取指导。"""
import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"yishi\app\src\main\java\com\kyf778\yishi\ai\CodeBuddyDirectProvider.kt"
t = open(p, encoding="utf-8").read()

# 在「找截止时间」之后、「优先级规则」之前插入字段说明
anchor = "⚠️ 优先级规则：如果\"用户补充说明\"里提到了用户自己要做什么的时间"

new_block = '''【location 和 notes：不要把细节丢掉】
通知里除了标题和时间，常常还有几类关键信息。它们必须落到对应字段，
绝不能因为「summary 只写一句话」就丢掉。

- location（地点）：
  「培训地点：30107」「腾讯会议 123-456-789」「实验楼 A301」「线上」
  → 只填地点本身，不要带「地点：」这类前缀。没有就返回 null。

- notes（补充说明）：
  装那些 summary 放不下的散装信息，尤其是这三类：
  ① 适用范围：「24级、25级、26级全体副班长」「仅限本班同学」
  ② 弹性/变更时间：「改到晚上8点」「有课的8:20下课来」「推迟半小时」
  ③ 硬性要求：「带学生证」「穿正装」「需提前10分钟到场」
  → 可以写多句，用「；」分隔。没有就返回 null。

- summary（摘要）：
  写「做什么 + 要准备什么」，控制在两句话内。
  ⚠️ 但**地点和范围限定不写进 summary**，它们有专门的字段。

判断原则：凡是原文里出现、但 title 和 deadline 都装不下的关键信息，
都要落到 location 或 notes 里。宁可 notes 长一点，也不要吞掉内容。

⚠️ 优先级规则：如果"用户补充说明"里提到了用户自己要做什么的时间'''

if anchor in t:
    t = t.replace(anchor, new_block, 1)
    print("✅ 提示词已补 location/notes 提取指导")
else:
    print("⚠️ 锚点未匹配")
    # 找位置
    i = t.find("优先级规则")
    if i > 0:
        print(f"   找到『优先级规则』在 {i}，附近内容：")
        print(repr(t[i-100:i+50]))

open(p, "w", encoding="utf-8", newline="\n").write(t)
