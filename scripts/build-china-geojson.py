#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""由 data/geo/raw/{code}_self.json 合并生成省级 GeoJSON（含 adcode/name）。"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(REPO, "data", "geo", "raw")
OUT = os.path.join(REPO, "frontend", "public", "geo", "china-provinces.json")

features = []
for name in sorted(os.listdir(RAW)):
    if not name.endswith("_self.json"):
        continue
    with open(os.path.join(RAW, name), encoding="utf-8") as fh:
        data = json.load(fh)
    for feature in data.get("features", []):
        props = feature.get("properties", {})
        if props.get("level") != "province":
            continue
        adcode = str(props.get("adcode", ""))
        if len(adcode) != 6 or not adcode.isdigit():
            continue
        features.append({
            "type": "Feature",
            "id": adcode,
            "properties": {
                "adcode": adcode,
                "name": props.get("name", adcode),
                "observationCount": 0,
                "speciesCount": 0,
                "studentCount": 0,
                "visualHeight": 4,
            },
            "geometry": feature.get("geometry"),
        })

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as fh:
    json.dump({"type": "FeatureCollection", "features": features}, fh, ensure_ascii=False)
print("written %d provinces -> %s" % (len(features), OUT))
