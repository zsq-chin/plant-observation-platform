#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V4 §40-41 恢复演练：从备份目录恢复 MySQL + plant-media（会覆盖当前数据！仅用于演练环境）。
用法：python -X utf8 scripts/restore-plant.py <备份目录>
"""
import os
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MYSQL = "jingxuan-mysql-1"


def db_password():
    with open(os.path.join(REPO, ".env"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("DB_PASSWORD="):
                return line.split("=", 1)[1].strip()
    raise SystemExit(".env 缺少 DB_PASSWORD")


def main():
    backup_dir = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "")
    if not os.path.isdir(backup_dir):
        raise SystemExit("用法: restore-plant.py <备份目录>")
    pwd = db_password()
    print("警告：将覆盖当前 MySQL 数据与 plant-media，仅应在演练/恢复环境执行。")
    subprocess.run(["docker", "exec", "-e", "MYSQL_PWD=" + pwd, MYSQL,
                    "mysql", "-ujingxuan", "-e", "DROP DATABASE IF EXISTS jingxuan; CREATE DATABASE jingxuan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"],
                   check=True)
    sql_file = os.path.join(backup_dir, "database", "jingxuan.sql")
    with open(sql_file, encoding="utf-8") as fh:
        result = subprocess.run(
            ["docker", "exec", "-i", "-e", "MYSQL_PWD=" + pwd, MYSQL,
             "mysql", "-ujingxuan", "--default-character-set=utf8mb4", "jingxuan"],
            stdin=fh, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if result.returncode != 0:
            raise SystemExit("恢复 SQL 失败: " + result.stderr[-3000:])
    tar_file = os.path.join(backup_dir, "plant-media", "plant-media.tar.gz")
    media_root = os.path.join(REPO, "data")
    subprocess.run(["tar", "-C", media_root, "-xzf", tar_file], check=True)
    print("恢复完成: " + backup_dir)
    print("下一步：docker compose restart backend，并执行 E2E/健康检查验证。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
