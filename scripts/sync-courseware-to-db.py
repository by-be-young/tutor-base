"""
把 tutor-nest-vue/public/data/articles.json + public/articles/**/*.md
同步到后端数据库（course_article / course_release）。

用法：
    python scripts/sync-courseware-to-db.py            # 同步全部科目
    python scripts/sync-courseware-to-db.py 高中英语    # 只同步指定科目

前提：需要在 tutor-base/backend/.env 里配好 DATABASE_URL / USERNAME / PASSWORD。
这个脚本只读 .env，直连 PostgreSQL，不经过后端 HTTP API。
"""

import json
import os
import re
import sys
import hashlib
import psycopg
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / "tutor-nest-vue"
INDEX = FRONTEND / "public" / "data" / "articles.json"
ARTICLES_ROOT = FRONTEND / "public" / "articles"
ENV_FILE = ROOT / "backend" / ".env"


def load_env():
    env = {}
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
    url = env["DATABASE_URL"]
    # jdbc:postgresql://host1:5432,host2:5432,host3:5432/db?sslmode=require
    # 取第一个 host:port
    m = re.search(r"jdbc:postgresql://([^,:/]+):(\d+)", url)
    host, port = m.group(1), m.group(2)
    m2 = re.search(r"/([^/?]+)\?", url)
    db = m2.group(1)
    user = env["DATABASE_USERNAME"]
    pw = env["DATABASE_PASSWORD"]
    return f"postgresql://{user}:{pw}@{host}:{port}/{db}?sslmode=require"


def main():
    subject_filter = sys.argv[1] if len(sys.argv) > 1 else None

    articles = json.loads(INDEX.read_text(encoding="utf-8"))

    # 按科目分组
    by_subject = {}
    for a in articles:
        by_subject.setdefault(a["series"], []).append(a)

    conn = psycopg.connect(load_env())
    cur = conn.cursor()

    # 找老师 id
    cur.execute("SELECT id FROM public.account WHERE username = 'young' AND status = 'active'")
    teacher_id = cur.fetchone()[0]
    print(f"teacher_id = {teacher_id}")

    for subject, items in by_subject.items():
        if subject_filter and subject != subject_filter:
            continue

        # 找当前 release
        cur.execute(
            """SELECT h.release_id, r.version
               FROM course_release_head h
               JOIN course_release r ON r.id = h.release_id
               WHERE h.teacher_account_id = %s AND h.subject = %s""",
            (teacher_id, subject),
        )
        head = cur.fetchone()
        if not head:
            print(f"  跳过 {subject}：无现有 release")
            continue
        old_release_id, old_version = head

        # 读当前 release 的文章，用于 carry-over（正文 blob 不变的直接沿用 hash）
        cur.execute(
            """SELECT article_id, legacy_article_id, title, series, logical_path,
                      content_hash, answer_hash, question_count
               FROM course_article WHERE release_id = %s""",
            (old_release_id,),
        )
        old_articles = {row[0]: row for row in cur.fetchall()}

        # 构建新 release 的文章列表
        new_articles = []
        for item in items:
            aid = item["id"]
            path = item["path"].replace("\\", "/")
            md_path = ARTICLES_ROOT / path
            if not md_path.exists():
                print(f"  ⚠ 缺失文件：{md_path}")
                continue
            markdown = md_path.read_text(encoding="utf-8")
            content_hash = hashlib.sha256(markdown.encode("utf-8")).hexdigest()

            # 答案 hash 从 article_answer_keys 读
            cur.execute(
                """SELECT question_id, answer_text, auto_grade
                   FROM article_answer_keys WHERE blog_id = %s""",
                (aid,),
            )
            answer_rows = cur.fetchall()
            answer_bytes = json.dumps(
                [{"questionId": r[0], "answer": r[1], "autoGrade": r[2]} for r in answer_rows],
                ensure_ascii=False,
            ).encode("utf-8")
            answer_hash = hashlib.sha256(answer_bytes).hexdigest()
            q_count = len(answer_rows)

            old = old_articles.get(aid)
            if old and old[5] == content_hash:
                # 正文没变，沿用旧的 answer_hash
                answer_hash = old[6]
                q_count = old[7]

            new_articles.append({
                "article_id": aid,
                "legacy_id": aid,
                "title": item["title"],
                "series": item["series"],
                "logical_path": path,
                "content_hash": content_hash,
                "answer_hash": answer_hash,
                "question_count": q_count,
            })

        # 创建新 release
        new_version = old_version + 1
        manifest_path = f"manifests/{teacher_id}/{subject}/v{new_version}.json"
        manifest = {
            "schemaVersion": 1,
            "teacherId": teacher_id,
            "subject": subject,
            "version": new_version,
            "publishedAt": datetime.now(timezone.utc).isoformat(),
            "articles": [
                {
                    "articleId": a["article_id"],
                    "legacyArticleId": a["legacy_id"],
                    "title": a["title"],
                    "series": a["series"],
                    "logicalPath": a["logical_path"],
                    "contentHash": a["content_hash"],
                    "answerHash": a["answer_hash"],
                    "questionCount": a["question_count"],
                }
                for a in new_articles
            ],
        }
        manifest_bytes = json.dumps(manifest, ensure_ascii=False).encode("utf-8")
        manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()

        cur.execute(
            """INSERT INTO course_release
               (teacher_account_id, subject, version, manifest_path, manifest_hash)
               VALUES (%s, %s, %s, %s, %s) RETURNING id""",
            (teacher_id, subject, new_version, manifest_path, manifest_hash),
        )
        new_release_id = cur.fetchone()[0]

        for a in new_articles:
            cur.execute(
                """INSERT INTO course_article
                   (release_id, teacher_account_id, article_id, legacy_article_id,
                    subject, title, series, logical_path, content_hash, answer_hash, question_count)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                (new_release_id, teacher_id, a["article_id"], a["legacy_id"],
                 subject, a["title"], a["series"], a["logical_path"],
                 a["content_hash"], a["answer_hash"], a["question_count"]),
            )

        cur.execute(
            """INSERT INTO course_release_head (teacher_account_id, subject, release_id, updated_at)
               VALUES (%s, %s, %s, now())
               ON CONFLICT (teacher_account_id, subject)
               DO UPDATE SET release_id = EXCLUDED.release_id, updated_at = now()""",
            (teacher_id, subject, new_release_id),
        )

        print(f"  {subject}: v{old_version} → v{new_version}（{len(new_articles)} 篇）")

    conn.commit()
    cur.close()
    conn.close()
    print("完成。")


if __name__ == "__main__":
    main()
