#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理自动化校验脚本产生的数据（通知校验观察、其照片行与通知）。

这些记录由 check-plant-notifications.py / check-photo-access.py 创建，
带「通知校验植物」「鉴权校验植物」等名字，混在演示数据里会污染展廊与地图。

用法：python scripts/cleanup-verification-data.py [--purge]
  默认：逻辑删除观察 + 删除照片行与通知
  --purge：额外删除磁盘上的原图与缩略图
"""
import argparse
import os
import subprocess
import sys

TEST_NAMES = ("通知校验植物", "鉴权校验植物", "接口校验植物", "探针植物")


def load_db_password():
    for path in (".env", "../.env"):
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("DB_PASSWORD="):
                    return line.split("=", 1)[1].strip()
    return os.environ.get("DB_PASSWORD", "")


def mysql(sql, password, container="jingxuan-mysql-1"):
    # --default-character-set=utf8mb4：容器内 mysql 客户端默认字符集不是 utf8mb4，
    # 否则中文条件匹配不到任何行（实测踩过）
    out = subprocess.run(
        ["docker", "exec", container, "mysql", "-ujingxuan", "-p" + password, "--default-character-set=utf8mb4",
         "-D", "jingxuan", "-N", "-B", "-e", sql],
        capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0 and "Using a password" not in (out.stderr or ""):
        print("SQL 失败:", (out.stderr or "").strip()[:200], file=sys.stderr)
    return (out.stdout or "").strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--purge", action="store_true", help="同时删除磁盘上的测试图片文件")
    args = parser.parse_args()

    password = load_db_password()
    if not password:
        print("未找到 DB_PASSWORD（.env）", file=sys.stderr)
        return 1

    names = ",".join("'%s'" % n for n in TEST_NAMES)
    ids = [x for x in mysql("select id from plant_observation where reported_common_name in (%s)" % names, password).split("\n") if x.strip()]
    if not ids:
        print("没有需要清理的校验数据")
        return 0
    id_list = ",".join(ids)
    print("待清理观察:", len(ids), "条")

    files = [f for f in mysql(
        "select concat(file_url, '|', ifnull(thumbnail_url,'')) from plant_photo where observation_id in (%s)" % id_list,
        password).split("\n") if f.strip()]

    mysql("delete from sys_notification where ref_id in (%s) and type like 'plant-%%'" % id_list, password)
    mysql("delete from plant_photo where observation_id in (%s)" % id_list, password)
    mysql("delete from plant_review where observation_id in (%s)" % id_list, password)
    mysql("delete from plant_observation where id in (%s)" % id_list, password)
    print("已删除观察/照片行/审核留痕/相关通知")

    if args.purge:
        removed = 0
        for entry in files:
            for url in entry.split("|"):
                url = url.strip()
                if not url.startswith("/media/plants/"):
                    continue
                rel = url[len("/media/plants/"):]
                host_path = os.path.join("data", "plant-media", rel.replace("/", os.sep))
                if os.path.exists(host_path):
                    os.remove(host_path)
                    removed += 1
        print("已删除磁盘文件:", removed, "个")

    left = mysql("select count(*) from plant_observation where reported_common_name in (%s)" % names, password)
    print("剩余校验观察:", left)
    return 0


if __name__ == "__main__":
    sys.exit(main())
