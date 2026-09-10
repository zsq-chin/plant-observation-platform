#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查地图 GeoJSON 数据完整性（PC 与 App 两份同源、含台湾省真实几何）。

用法： python -X utf8 scripts/check-china-geojson.py
退出码：0 通过；1 存在问题
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = [
    ("PC 完整版", os.path.join(REPO, "frontend", "public", "geo", "china-provinces.json")),
    ("App 简化版", os.path.join(REPO, "student-app", "src", "static", "geo", "china-provinces.json")),
]
REQUIRED_CODES = ["110000", "310000", "440000", "510000", "650000", "710000", "810000", "820000"]
FAIL = []


def check(cond, msg):
    print(("[PASS] " if cond else "[FAIL] ") + msg)
    if not cond:
        FAIL.append(msg)


def point_count(coords):
    total = 0
    stack = [coords]
    while stack:
        item = stack.pop()
        if isinstance(item, list) and len(item) == 2 and all(isinstance(x, (int, float)) for x in item):
            total += 1
        elif isinstance(item, list):
            stack.extend(item)
    return total


def main():
    summary = {}
    for label, path in FILES:
        if not os.path.exists(path):
            check(False, "%s 文件存在：%s" % (label, path))
            continue
        data = json.load(open(path, encoding="utf-8"))
        feats = data.get("features", [])
        codes = [str(f.get("id") or (f.get("properties") or {}).get("adcode") or "") for f in feats]
        check(len(feats) >= 34, "%s 省级要素数量 >= 34（实际 %d）" % (label, len(feats)))
        check(len(set(codes)) == len(codes), "%s 省份编码无重复" % label)
        for code in REQUIRED_CODES:
            check(code in codes, "%s 包含 %s" % (label, code))
        taiwan = next((f for f in feats if str(f.get("id")) == "710000"), None)
        geometry = (taiwan or {}).get("geometry") or {}
        coords = geometry.get("coordinates") or []
        points = point_count(coords)
        check(bool(taiwan), "%s 存在台湾省要素" % label)
        check((taiwan or {}).get("properties", {}).get("name") == "台湾省", "%s 台湾省名称正确" % label)
        check((taiwan or {}).get("properties", {}).get("adcode") in ("710000", 710000), "%s 台湾省 adcode 正确" % label)
        check(geometry.get("type") in ("Polygon", "MultiPolygon"), "%s 台湾省几何类型合法（%s）" % (label, geometry.get("type")))
        check(points > 20, "%s 台湾省坐标点充足（实际 %d）" % (label, points))
        summary[label] = set(codes)
    if len(summary) == 2:
        pc, app = summary["PC 完整版"], summary["App 简化版"]
        check(pc == app, "PC 与 App 两份地图省份集合一致（%d 省）" % len(pc))
    print("")
    if FAIL:
        print("检查失败项：%d" % len(FAIL))
        for item in FAIL:
            print("  -", item)
        return 1
    print("地图 GeoJSON 检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
