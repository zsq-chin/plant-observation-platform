#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V3 §24/25 全国行政区可视化数据导入（DataV GeoJSON 来源）。

来源：https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json
说明：GeoJSON 仅用于行政边界展示数据提取（bbox/质心锚点），边界绘制与合规要求见
docs/plant-map-data-source-and-compliance.md。锚点是行政区展示中心，不是学生位置。

用法：python -X utf8 scripts/import-plant-regions.py [--districts]
输出：data/geo/regions-import.sql（再通过 docker exec mysql 应用）；原料缓存 data/geo/raw/
"""
import argparse
import json
import math
import os
import subprocess
import sys
import time
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(REPO, "data", "geo", "raw")
OUT_SQL = os.path.join(REPO, "data", "geo", "regions-import.sql")
DATAV = "https://geo.datav.aliyun.com/areas_v3/bound/{}_full.json"
USER_AGENT = "Mozilla/5.0 (plant-platform-region-import)"

PROVINCE_CODES = [
    "110000","120000","130000","140000","150000","210000","220000","230000","310000","320000",
    "330000","340000","350000","360000","370000","410000","420000","430000","440000","450000",
    "460000","500000","510000","520000","530000","540000","610000","620000","630000","640000",
    "650000","710000","810000","820000",
]


def fetch(adcode):
    path = os.path.join(RAW_DIR, adcode + ".json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    url = DATAV.format(adcode)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            os.makedirs(RAW_DIR, exist_ok=True)
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(data, fh, ensure_ascii=False)
            return data
        except Exception as exc:
            if attempt == 3:
                raise
            time.sleep(2 * (attempt + 1))


def fetch_self(adcode):
    path = os.path.join(RAW_DIR, adcode + "_self.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    url = DATAV.replace("_full", "").format(adcode)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            os.makedirs(RAW_DIR, exist_ok=True)
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(data, fh, ensure_ascii=False)
            return data
        except Exception as exc:
            if attempt == 3:
                raise
            time.sleep(2 * (attempt + 1))

def geometry_info(geometry):
    coords = []
    polys = geometry.get("coordinates", []) if geometry else []
    if geometry and geometry.get("type") == "Polygon":
        polys = [polys]
    rings = []
    for poly in polys:
        if poly:
            rings.append(poly[0])
    if not rings:
        return None
    lats = []
    lngs = []
    for ring in rings:
        for pt in ring:
            lngs.append(pt[0])
            lats.append(pt[1])
    if not lngs:
        return None
    return {
        "min_lng": round(min(lngs), 6),
        "min_lat": round(min(lats), 6),
        "max_lng": round(max(lngs), 6),
        "max_lat": round(max(lats), 6),
        "center_lng": round((min(lngs) + max(lngs)) / 2, 6),
        "center_lat": round((min(lats) + max(lats)) / 2, 6),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--districts", action="store_true", help="同时导入区县级")
    parser.add_argument("--apply", action="store_true", help="应用 SQL 到 docker MySQL")
    args = parser.parse_args()

    statements = []
    seen = set()

    def upsert(code, name, parent, level, info, sort_hint):
        if code in seen:
            return
        seen.add(code)
        vals = [code, name.replace("'", "''"), parent or "NULL", level, int(sort_hint)]
        if info:
            vals.append(info["center_lng"])
            vals.append(info["center_lat"])
            vals.append(info["min_lng"])
            vals.append(info["min_lat"])
            vals.append(info["max_lng"])
            vals.append(info["max_lat"])
        else:
            vals += ["NULL"] * 6
        if level == "PROVINCE":
            sql = ("INSERT INTO sys_region (id, region_code, region_name, parent_code, region_level, sort_order,"
                   " center_lng, center_lat, min_lng, min_lat, max_lng, max_lat) VALUES (%d,'%s','%s',%s,'%s',%d,%s,%s,%s,%s,%s,%s) "
                   "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name), center_lng=VALUES(center_lng),"
                   " center_lat=VALUES(center_lat), min_lng=VALUES(min_lng), min_lat=VALUES(min_lat),"
                   " max_lng=VALUES(max_lng), max_lat=VALUES(max_lat)" % ((int(code),) + tuple(vals)))
        else:
            sql = ("INSERT INTO sys_region (id, region_code, region_name, parent_code, region_level, sort_order,"
                   " center_lng, center_lat, min_lng, min_lat, max_lng, max_lat) VALUES (%d,'%s','%s','%s','%s',%d,%s,%s,%s,%s,%s,%s) "
                   "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name), parent_code=VALUES(parent_code),"
                   " region_level=VALUES(region_level), center_lng=VALUES(center_lng), center_lat=VALUES(center_lat),"
                   " min_lng=VALUES(min_lng), min_lat=VALUES(min_lat), max_lng=VALUES(max_lng), max_lat=VALUES(max_lat)"
                   % ((int(code),) + tuple(vals)))
        statements.append(sql + ";")

    print("下载/解析省级…")
    cities_by_province = {}
    municipalities = {}
    for idx, code in enumerate(PROVINCE_CODES):
        try:
            data = fetch(code)
        except Exception as exc:
            print("  省级 %s 拉取失败(跳过): %s" % (code, exc))
            continue
        features = data.get("features", [])
        info = None
        name = code
        try:
            self_data = fetch_self(code)
            for feature in self_data.get("features", []):
                props = feature.get("properties", {})
                if str(props.get("adcode", "")) == code:
                    info = geometry_info(feature.get("geometry"))
                    name = props.get("name", code)
                    break
        except Exception as exc:
            print("  省级 %s 自身几何拉取失败(跳过): %s" % (code, exc))
        upsert(code, name, None, "PROVINCE", info, idx + 1)
        # children (cities) inside full geojson are features with level city & adcode != code
        children = []
        districts_under_province = []
        for feature in features:
            props = feature.get("properties", {})
            if str(props.get("adcode", "")) != code and props.get("level") == "city":
                children.append(feature)
            elif str(props.get("adcode", "")) != code and props.get("level") == "district":
                districts_under_province.append(feature)
        cities_by_province[code] = children
        municipalities[code] = districts_under_province
        print("  省级 %s 完成，城市数 %d" % (code, len(children)))

    print("下载/解析市级…")
    for code, children in cities_by_province.items():
        direct_districts = municipalities.get(code, [])
        if not children and direct_districts:
            for feature in direct_districts:
                p2 = feature.get("properties", {})
                dcode = str(p2.get("adcode", ""))
                dinfo = geometry_info(feature.get("geometry"))
                upsert(dcode, p2.get("name", dcode), code, "DISTRICT", dinfo, int(dcode[-2:] or 0))
            print("  省级 %s 直辖市-直接区县 %d 完成" % (code, len(direct_districts)))
            continue
        for child in children:
            props = child.get("properties", {})
            child_code = str(props.get("adcode", ""))
            info = geometry_info(child.get("geometry"))
            upsert(child_code, props.get("name", child_code), code, "CITY", info, int(child_code[-4:-2] or 0))
            if args.districts:
                try:
                    data = fetch(child_code)
                except Exception as exc:
                    print("  区县拉取失败 %s: %s" % (child_code, exc))
                    continue
                for feature in data.get("features", []):
                    p2 = feature.get("properties", {})
                    if p2.get("level") == "district" or str(p2.get("adcode", "")).startswith(child_code[:4]):
                        dcode = str(p2.get("adcode", ""))
                        dinfo = geometry_info(feature.get("geometry"))
                        upsert(dcode, p2.get("name", dcode), child_code, "DISTRICT", dinfo,
                               int(dcode[-2:] or 0))
        print("  省级 %s 市级处理完成" % code)

    os.makedirs(os.path.dirname(OUT_SQL), exist_ok=True)
    with open(OUT_SQL, "w", encoding="utf-8") as fh:
        fh.write("-- 由 import-plant-regions.py 生成（DataV 来源，见合规文档）\n")
        fh.write("\n".join(statements))
        fh.write("\n")
    print("SQL 已写出：%s（%d 条语句）" % (OUT_SQL, len(statements)))

    if args.apply:
        print("应用到 docker MySQL…")
        pwd = None
        with open(os.path.join(REPO, ".env"), encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line.startswith("DB_PASSWORD="):
                    pwd = line.split("=", 1)[1].strip()
        if not pwd:
            raise SystemExit(".env 缺少 DB_PASSWORD")
        with open(OUT_SQL, encoding="utf-8") as fh:
            result = subprocess.run(
                ["docker", "exec", "-i", "-e", "MYSQL_PWD=" + pwd, "jingxuan-mysql-1",
                 "mysql", "-ujingxuan", "--default-character-set=utf8mb4", "jingxuan"],
                stdin=fh, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if result.returncode != 0:
            print(result.stderr[-3000:])
            raise SystemExit(result.returncode)
        print("SQL 应用完成")


if __name__ == "__main__":
    main()
