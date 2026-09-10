#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""下线演示环境中的测试图观察记录（1x1 测试 PNG 产生的 70B/633B 图片）。

做法：
  1) 用 docker exec + mysql 找出所有照片体积 < 2048 字节的 APPROVED 观察记录（这些是冒烟/E2E 产生的空白卡片）
  2) 以管理员身份调用 POST /api/admin/plant/observations/{id}/offline 强制下线（保留数据，仅移出展廊/地图/首页）
  3) 复核展廊：不再出现体积过小的封面

用法：
  python -X utf8 scripts/cleanup-demo-test-photos.py --base http://127.0.0.1:8080 --mysql-container jingxuan-mysql-1
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request


def load_db_password():
    for path in (".env", "../.env"):
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("DB_PASSWORD="):
                    return line.split("=", 1)[1].strip()
    return os.environ.get("DB_PASSWORD", "")


def http_json(method, url, token=None, body=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            return exc.code, json.loads(raw)
        except Exception:
            return exc.code, {"raw": raw}


def mysql_query(container, password, sql, db="jingxuan", user="jingxuan"):
    cmd = ["docker", "exec", "-e", "MYSQL_PWD=" + password, container,
           "mysql", "-u" + user, "-N", "-B", db, "-e", sql]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError("mysql 执行失败: " + (proc.stderr or "").strip())
    return proc.stdout


def tiny_photo_observations(container, password, threshold, db="jingxuan", user="jingxuan"):
    sql = ("SELECT DISTINCT o.id FROM plant_observation o JOIN plant_photo p ON p.observation_id = o.id "
           "WHERE o.deleted = 0 AND p.deleted = 0 AND p.file_size < %d AND o.status = 'APPROVED'" % threshold)
    cmd = ["docker", "exec", "-e", "MYSQL_PWD=" + password, container,
           "mysql", "-u" + user, "-N", "-B", db, "-e", sql]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError("mysql 查询失败: " + (proc.stderr or "").strip())
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def probe(url):
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, int(resp.headers.get("Content-Length") or 0)
    except Exception:
        return -1, 0


def purge_all(base, args, db_password):
    """演示环境专用：删除（逻辑）所有含测试图的观察与照片，并清理磁盘上的小图文件。"""
    sql_ids = ("SELECT DISTINCT o.id FROM plant_observation o JOIN plant_photo p ON p.observation_id = o.id "
               "WHERE o.deleted = 0 AND p.deleted = 0 AND p.file_size < %d" % args.threshold)
    ids = [line.strip() for line in mysql_query(args.mysql_container, db_password, sql_ids).splitlines() if line.strip()]
    print("待彻底清理的观察记录: %d 条" % len(ids))
    if not ids:
        return 0
    id_list = ",".join(ids)
    sql_urls = ("SELECT file_url, thumbnail_url FROM plant_photo WHERE observation_id IN (%s) AND deleted = 0" % id_list)
    urls = [line.split("\t") for line in mysql_query(args.mysql_container, db_password, sql_urls).splitlines() if line.strip()]
    if args.dry_run:
        print("dry-run，将清理: " + id_list)
        return 0
    sql_update = ("UPDATE plant_photo SET deleted = 1 WHERE observation_id IN (%s);" % id_list) + \
                 ("UPDATE plant_observation SET deleted = 1 WHERE id IN (%s);" % id_list)
    mysql_query(args.mysql_container, db_password, sql_update)
    print("已逻辑删除观察 %d 条及其照片记录" % len(ids))
    removed = 0
    for pair in urls:
        for url in pair:
            path = os.path.join("data/plant-media", url.replace("/media/plants/", ""))
            if os.path.isfile(path) and os.path.getsize(path) < args.threshold:
                try:
                    os.remove(path)
                    removed += 1
                except OSError:
                    pass
    print("已删除磁盘上的测试小图文件: %d 个" % removed)
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--mysql-container", default="jingxuan-mysql-1")
    parser.add_argument("--threshold", type=int, default=2048)
    parser.add_argument("--admin", default="admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--purge", action="store_true",
                        help="彻底清理：逻辑删除所有含小图（测试图）的观察记录与照片行，并删除其物理小文件（仅演示环境使用）")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    db_password = load_db_password()
    if not db_password:
        print("未找到 DB_PASSWORD（.env 或环境变量）")
        return 2
    try:
        ids = tiny_photo_observations(args.mysql_container, db_password, args.threshold)
    except Exception as exc:
        print("查询失败: %s" % exc)
        return 2
    if args.purge:
        return purge_all(base, args, db_password)
    print("待下线的测试图观察记录: %d 条" % len(ids))
    if not ids:
        return 0

    status, payload = http_json("POST", base + "/api/auth/login",
                                body={"username": args.admin, "password": args.password})
    if payload.get("code") != 200:
        print("管理员登录失败: " + json.dumps(payload, ensure_ascii=False))
        return 2
    token = payload["data"]["token"]

    if args.dry_run:
        print("dry-run，将下线: " + ", ".join(ids))
        return 0

    ok = 0
    failed = []
    for obs_id in ids:
        st, res = http_json("POST", base + "/api/admin/plant/observations/" + obs_id + "/offline", token, {})
        if res.get("code") == 200:
            ok += 1
        else:
            failed.append((obs_id, res.get("message")))
    print("已下线 %d 条，失败 %d 条" % (ok, len(failed)))
    for obs_id, msg in failed:
        print("  - %s: %s" % (obs_id, msg))

    print("")
    print("=== 展廊复核（前 12 条封面体积） ===")
    st, gallery = http_json("GET", base + "/api/public/plant/gallery?sort=latest&size=12")
    small = 0
    for row in ((gallery.get("data") or {}).get("records") or []):
        cover = row.get("coverUrl") or ""
        code, length = probe(base + cover)
        flag = "[SMALL]" if (code == 200 and length < args.threshold) else "[OK]"
        if code == 200 and length < args.threshold:
            small += 1
        print("%s %-14s %s -> %s %d bytes" % (flag, row.get("commonName") or row.get("reportedCommonName") or "-",
                                              row.get("observationId"), code, length))
    if small:
        print("仍有 %d 条小体积封面" % small)
        return 1
    print("展廊封面均为真实图片")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())