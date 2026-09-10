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

# ---------------------------------------------------------------------------
# 移动端（学生 App）专用简化版：坐标保留 2 位小数 + 环形抽稀，显著减小体积，
# 供 App 内置 2D 中国地图使用（省份边界在城市尺度下足够清晰）。
# ---------------------------------------------------------------------------
MOBILE_OUT = os.path.join(REPO, "student-app", "src", "static", "geo", "china-provinces.json")
SIMPLIFY_TOLERANCE = 0.06  # 度


def _perpendicular_distance(point, start, end):
    if start == end:
        return ((point[0] - start[0]) ** 2 + (point[1] - start[1]) ** 2) ** 0.5
    numerator = abs((end[1] - start[1]) * point[0] - (end[0] - start[0]) * point[1] + end[0] * start[1] - end[1] * start[0])
    denominator = ((end[1] - start[1]) ** 2 + (end[0] - start[0]) ** 2) ** 0.5
    return numerator / denominator if denominator else 0.0


def _simplify(points, tolerance):
    """Douglas-Peucker 抽稀（迭代实现，避免深递归）。"""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        max_dist, index = 0.0, -1
        for i in range(first + 1, last):
            dist = _perpendicular_distance(points[i], points[first], points[last])
            if dist > max_dist:
                max_dist, index = dist, i
        if max_dist > tolerance and index > 0:
            keep[index] = True
            stack.append((first, index))
            stack.append((index, last))
    return [p for p, flag in zip(points, keep) if flag]


def _round_ring(ring):
    simplified = _simplify([[round(x, 2), round(y, 2)] for x, y in ring], SIMPLIFY_TOLERANCE)
    return simplified if len(simplified) >= 4 else [[round(x, 2), round(y, 2)] for x, y in ring]


mobile_features = []
for feature in features:
    geometry = feature.get("geometry") or {}
    gtype = geometry.get("type")
    if gtype == "Polygon":
        coordinates = [_round_ring(ring) for ring in geometry.get("coordinates", [])]
    elif gtype == "MultiPolygon":
        coordinates = [[_round_ring(ring) for ring in polygon] for polygon in geometry.get("coordinates", [])]
    else:
        continue
    mobile_features.append({
        "type": "Feature",
        "id": feature["id"],
        "properties": {"adcode": feature["properties"]["adcode"], "name": feature["properties"]["name"]},
        "geometry": {"type": gtype, "coordinates": coordinates},
    })

os.makedirs(os.path.dirname(MOBILE_OUT), exist_ok=True)
with open(MOBILE_OUT, "w", encoding="utf-8") as fh:
    json.dump({"type": "FeatureCollection", "features": mobile_features}, fh, ensure_ascii=False, separators=(",", ":"))
print("written %d provinces (mobile, %.0fKB) -> %s" % (
    len(mobile_features), os.path.getsize(MOBILE_OUT) / 1024, MOBILE_OUT))
