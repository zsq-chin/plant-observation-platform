#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""像素级可见性验证：采样台湾与相邻省份/海面像素，判断台湾岛是否真的看得见。"""
import json
from PIL import Image
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1"
SAMPLES = {
    "台湾本岛": [121.0, 23.7],
    "台湾南部": [120.6, 22.6],
    "四川": [103.8, 30.6],
    "浙江": [120.2, 29.2],
    "新疆": [85.0, 41.5],
    "海面(台湾以东)": [124.5, 23.7],
    "海面(黄海)": [123.0, 35.0],
}

with sync_playwright() as p:
    browser = p.chromium.launch(args=["--no-sandbox"])
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    page.goto(BASE + "/jingxuan/plant/map", wait_until="load")
    page.wait_for_timeout(6000)
    box = page.locator(".map-canvas").bounding_box()
    points = page.evaluate("""(samples) => {
      const map = window.__plantMap;
      const out = {};
      for (const [label, lngLat] of Object.entries(samples)) {
        const pt = map.project(lngLat);
        out[label] = { x: Math.round(pt.x), y: Math.round(pt.y) };
      }
      return out;
    }""", SAMPLES)
    page.screenshot(path="tmp-ui-check/pc-map-pixels.png")
    browser.close()

img = Image.open("tmp-ui-check/pc-map-pixels.png").convert("RGB")
print("地图容器:", json.dumps(box))
print("采样点（容器内坐标 → 全页坐标 → RGB）:")
bg = None
for label, pt in points.items():
    gx = int(box["x"] + pt["x"])
    gy = int(box["y"] + pt["y"])
    rgb = img.getpixel((gx, gy))
    if label.startswith("海面"):
        bg = rgb if bg is None else bg
    print("  %-14s (%4d,%4d) -> %s" % (label, pt["x"], pt["y"], rgb))

def diff(a, b):
    return sum(abs(x - y) for x, y in zip(a, b))

sea = img.getpixel((int(box["x"] + points["海面(黄海)"]["x"]), int(box["y"] + points["海面(黄海)"]["y"])))
for label in ("台湾本岛", "台湾南部", "四川", "浙江", "新疆"):
    pt = points[label]
    rgb = img.getpixel((int(box["x"] + pt["x"]), int(box["y"] + pt["y"])))
    print("  %-10s 与海面背景色差 = %d %s" % (label, diff(rgb, sea), "(肉眼难辨!)" if diff(rgb, sea) < 25 else ""))