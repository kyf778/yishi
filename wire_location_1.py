"""为 location / extra_note 打通 DAO → Repository → UI 全链路。"""
import io, sys, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"yishi\app\src\main\java\com\kyf778\yishi"

# ── 1. DAO：4 处 SELECT 加字段 ──
p = ROOT + r"\data\Daos.kt"
t = open(p, encoding="utf-8").read()
old = "e.created_at AS createdAt, e.date_only AS dateOnly"
new = "e.created_at AS createdAt, e.date_only AS dateOnly,\n               e.location AS location, e.extra_note AS extraNote"
cnt = t.count(old)
t = t.replace(old, new)
open(p, "w", encoding="utf-8", newline="\n").write(t)
print(f"✅ Daos.kt: {cnt} 处 SELECT 已加 location/extraNote")

# ── 2. 数据库版本 + 迁移 ──
p2 = ROOT + r"\data\AppDatabase.kt"
t2 = open(p2, encoding="utf-8").read()
t2 = t2.replace("    version = 4,", "    version = 5,")

migration = '''
        /**
         * v4 → v5：补上 AI 识别了、但一直没落库的两个字段。
         *
         * 起因：一条「副班长培训会」的通知里写了「培训地点：30107」、
         * 「24级、25级、26级全体副班长」、「有课的8:20下课来」，AI 全都识别出来了，
         * 但建卡时只取了标题/摘要/时间，地点和补充说明直接丢弃 ——
         * 因为数据库根本没有列可以存。用户看到卡片才发现「吞掉了好多内容」。
         *
         * location     : 地点（会议室号、线上会议链接等）
         * extra_note   : 范围限定、弹性时间等散装信息
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE countdown_event ADD COLUMN location TEXT")
                db.execSQL("ALTER TABLE countdown_event ADD COLUMN extra_note TEXT")
            }
        }

'''

anchor = "        @Volatile private var instance: AppDatabase? = null"
if "MIGRATION_4_5" not in t2:
    t2 = t2.replace(anchor, migration + anchor, 1)
    print("✅ AppDatabase.kt: MIGRATION_4_5 已加")

t2 = t2.replace(".addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)",
                ".addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)")
print("✅ AppDatabase.kt: addMigrations 已更新")

open(p2, "w", encoding="utf-8", newline="\n").write(t2)
print(f"   版本: {[l.strip() for l in t2.split(chr(10)) if 'version =' in l]}")
