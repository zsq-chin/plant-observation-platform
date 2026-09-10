#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V3 §17 旧植物图片迁移：/uploads/plant/** -> data/plant-media(original) 并更新 plant_photo。
前置：docker 栈运行（backend 挂载 ./data/plant-media 与 /app/uploads 卷）。
用法：python -X utf8 scripts/migrate-plant-images.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_ROOT = os.path.join(REPO, "data", "plant-media")
BACKEND = "jingxuan-backend-1"
MYSQL = "jingxuan-mysql-1"
BASE_URL = os.environ.get("PLANT_BASE_URL", "http://127.0.0.1:8080")


def run(args, check=True):
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and result.returncode != 0:
        raise RuntimeError("命令失败 %s: %s" % (args, result.stderr[-2000:]))
    return result


def db_password():
    with open(os.path.join(REPO, ".env"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("DB_PASSWORD="):
                return line.split("=", 1)[1].strip()
    raise RuntimeError(".env 缺少 DB_PASSWORD")


def query(sql):
    pwd = db_password()
    result = run([
        "docker", "exec", "-e", "MYSQL_PWD=" + pwd, MYSQL,
        "mysql", "-ujingxuan", "-N", "-B", "--default-character-set=utf8mb4",
        "-e", sql, "jingxuan",
    ])
    rows = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        rows.append(line.split("\t"))
    return rows


def migrate():
    os.makedirs(DATA_ROOT, exist_ok=True)
    rows = query("SELECT id, observation_id, file_url FROM plant_photo WHERE file_url LIKE '/uploads/plant/%' AND deleted=0")
    if not rows:
        print("没有需要迁移的旧路径图片")
        return 0
    migrated = 0
    failed = 0
    for row in rows:
        photo_id, obs_id, old_url = row[0], row[1], row[2]
        rel = old_url[len("/uploads/"):]
        container_path = "/app/uploads/" + rel
        name = rel.rsplit("/", 1)[-1]
        try:
            probe = run(["docker", "exec", BACKEND, "sh", "-c",
                         "test -f " + container_path + " && echo yes"], check=False)
            if probe.returncode != 0 or "yes" not in probe.stdout:
                print("[SKIP] 容器内文件缺失 %s (photo=%s)" % (container_path, photo_id))
                failed += 1
                continue
            tmp = os.path.join(tempfile.gettempdir(), name)
            run(["docker", "cp", BACKEND + ":" + container_path, tmp])
            target_dir = os.path.join(DATA_ROOT, "original", str(obs_id))
            os.makedirs(target_dir, exist_ok=True)
            target = os.path.join(target_dir, name)
            shutil.move(tmp, target)
            new_url = "/media/plants/original/" + str(obs_id) + "/" + name
            query("UPDATE plant_photo SET file_url='" + new_url + "', thumbnail_url='" + new_url + "' WHERE id=" + photo_id)
            try:
                with urllib.request.urlopen(BASE_URL + new_url, timeout=10) as resp:
                    ok = resp.status == 200 and (resp.headers.get("Content-Type") or "").startswith("image")
            except Exception:
                ok = False
            if ok:
                migrated += 1
                print("[OK] photo=%s obs=%s -> %s" % (photo_id, obs_id, new_url))
            else:
                migrated += 1
                print("[WARN] 已迁移但 HTTP 验证未通过 photo=%s %s" % (photo_id, new_url))
        except Exception as exc:
            print("[FAIL] photo=%s %s: %s" % (photo_id, old_url, exc))
            failed += 1
    print("迁移完成：成功 %s，失败/跳过 %s" % (migrated, failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(migrate())
