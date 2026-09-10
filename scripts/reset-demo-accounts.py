#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""重置演示账号密码并解除登录防爆破锁定（仅用于本地演示环境）。

用法：
  python -X utf8 scripts/reset-demo-accounts.py --users stu1,stu2,tea1,admin --password admin123
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

# 已在本仓库验证过对应 admin123 的 BCrypt 哈希（cost 12）
DEFAULT_HASH = "$2y$12$kPA2yToOV.VuyoQgL1PgzezYIslEosimPcrEyzKQ.OcuVdzRB/I4K"


def load_env(path=".env"):
    values = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if "=" in line and not line.strip().startswith("#"):
                    k, v = line.split("=", 1)
                    values[k.strip()] = v.strip()
    return values


def run(cmd, stdin_text=None):
    proc = subprocess.run(cmd, input=stdin_text, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "").strip())
    return proc.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--users", default="stu1,stu2,tea1,admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--hash", default="", help="自定义 BCrypt 哈希（默认使用 admin123 的已验证哈希）")
    parser.add_argument("--mysql-container", default="jingxuan-mysql-1")
    parser.add_argument("--redis-container", default="jingxuan-redis-1")
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()

    env = load_env()
    db_password = env.get("DB_PASSWORD") or os.environ.get("DB_PASSWORD", "")
    db_user = env.get("DB_USER", "jingxuan")
    db_name = env.get("DB_NAME", "jingxuan")
    if not db_password:
        print("未找到 DB_PASSWORD")
        return 2

    users = [u.strip() for u in args.users.split(",") if u.strip()]
    hashed = args.hash or DEFAULT_HASH
    quoted = ",".join("'" + u + "'" for u in users)
    sql = ("UPDATE sys_user SET password = '%s', status = 1, deleted = 0, first_login = 0 "
           "WHERE username IN (%s);" % (hashed, quoted))
    out = run(["docker", "exec", "-i", "-e", "MYSQL_PWD=" + db_password, args.mysql_container,
               "mysql", "-u" + db_user, db_name], stdin_text=sql)
    print("已重置账号: " + ", ".join(users))

    for user in users:
        try:
            run(["docker", "exec", args.redis_container, "redis-cli", "DEL", "jingxuan:login:fail:" + user.lower()])
        except Exception as exc:
            print("  清理锁定失败 %s: %s" % (user, exc))
    print("已清理 Redis 登录失败计数")

    print("")
    for user in users:
        body = json.dumps({"username": user, "password": args.password}).encode("utf-8")
        req = urllib.request.Request(args.base + "/api/auth/login", data=body, method="POST",
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            payload = json.loads(exc.read().decode("utf-8", errors="replace"))
        print("  %-6s 登录 -> %s" % (user, payload.get("code")))
    return 0


if __name__ == "__main__":
    sys.exit(main())