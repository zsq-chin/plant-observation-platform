#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V4 §37-39 备份：MySQL 全量 + data/plant-media + Manifest。
用法：python -X utf8 scripts/backup-plant.py [目标目录，默认 ../backup/YYYYMMDD-HHMMSS]
"""
import datetime
import json
import os
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMPOSE = os.path.join(REPO, "docker-compose.yml")
MYSQL = "jingxuan-mysql-1"


def db_password():
    with open(os.path.join(REPO, ".env"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("DB_PASSWORD="):
                return line.split("=", 1)[1].strip()
    raise SystemExit(".env 缺少 DB_PASSWORD")


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        REPO, "backup", datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    db_dir = os.path.join(target, "database")
    media_dir = os.path.join(target, "plant-media")
    os.makedirs(db_dir, exist_ok=True)
    os.makedirs(media_dir, exist_ok=True)

    pwd = db_password()
    db_file = os.path.join(db_dir, "jingxuan.sql")
    with open(db_file, "w", encoding="utf-8") as fh:
        result = subprocess.run(
            ["docker", "exec", "-e", "MYSQL_PWD=" + pwd, MYSQL,
             "mysqldump", "-ujingxuan", "--single-transaction", "--routines", "--triggers", "jingxuan"],
            stdout=fh, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
        if result.returncode != 0:
            raise SystemExit("mysqldump 失败: " + result.stderr[-2000:])

    media_root = os.path.join(REPO, "data", "plant-media")
    subprocess.run(["tar", "-C", os.path.join(REPO, "data"), "-czf",
                    os.path.join(media_dir, "plant-media.tar.gz"), "plant-media"], check=True)

    db_size = os.path.getsize(db_file)
    media_size = os.path.getsize(os.path.join(media_dir, "plant-media.tar.gz"))
    manifest = {
        "createdAt": datetime.datetime.now().astimezone().isoformat(),
        "tool": "scripts/backup-plant.py",
        "components": [
            {"name": "mysql", "file": "database/jingxuan.sql", "bytes": db_size, "sha256": sha256(db_file)},
            {"name": "plant-media", "file": "plant-media/plant-media.tar.gz", "bytes": media_size, "sha256": sha256(os.path.join(media_dir, "plant-media.tar.gz"))},
        ],
        "restoreCommand": "python -X utf8 scripts/restore-plant.py " + target,
    }
    with open(os.path.join(target, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    print("备份完成: " + target)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


def sha256(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


if __name__ == "__main__":
    sys.exit(main())
