#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""台湾岛可见性细查：在台湾经纬度网格上采样，分类为 填充/柱体/背景。"""
from PIL import Image
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1"
# 台湾本岛大致范围 120.0~122.1E, 21.9~25.3N
GRID = [(lng, lat) for lng in (120.2, 120.6, 121.0, 121.4, 121.8) for lat in (22.2, 22.8, 23.4, 24.0, 24.6)]

with sync_playwright() as p:
    browser = p.chromium.launch(args=["--no-sandbox"])
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    page.goto(BASE + "/jingxuan/plant/map", wait_until="load")
    page.wait_for_timeout(6000)
    box = page.locator(".map-canvas").bounding_box()
    info = page.evaluate("""(grid) => {
      const map = window.__plantMap;
      const out = [];
      for (const [lng, lat] of grid) {
        const pt = map.project([lng, lat]);
        const feats = map.queryRenderedFeatures([[pt.x, pt.y], [pt.x + 1, pt.y + 1]], {
          layers: ['province-fill', 'province-extrusion'],
        });
        out.push({ lng, lat, x: Math.round(pt.x), y: Math.round(pt.y), codes: feats.map((f) => String((f.properties || {}).adcode)) });
      }
      return out;
    }""", GRID)
    page.screenshot(path="tmp-ui-check/taiwan-grid.png")
    browser.close()

img = Image.open("tmp-ui-check/taiwan-grid.png").convert("RGB")
bg = (234, 242, 226)
fill_new = (198, 223, 168)   # #c6dfa8 无数据填充
fill_old = (216, 231, 204)   # #d8e7cc 旧填充

def diff(a, b):
    return sum(abs(x - y) for x, y in zip(a, b))

in_taiwan = [i for i in info if "710000" in i["codes"]]
print("采样点: %d，其中命中台湾省(710000)的: %d" % (len(info), len(in_taiwan)))
print("说明: 命中即代表该点落在台湾岛几何内（province-fill/extrusion）")
print("")
print("%-8s %-6s %-10s %-16s %-8s %s" % ("经度", "纬度", "像素", "命中", "RGB", "与背景色差"))
visible = 0
for item in info:
    rgb = img.getpixel((int(box["x"] + item["x"]), int(box["y"] + item["y"])))
    d = diff(rgb, bg)
    tag = ",".join(item["codes"]) or "-"
    if "710000" in item["codes"] and d >= 60:
        visible += 1
    print("%-8.1f %-6.1f (%4d,%4d) %-16s %-8s %d" % (item["lng"], item["lat"], item["x"], item["y"], tag, str(rgb), d))
print("")
print("命中台湾且与背景色差 >= 60（肉眼可辨）的点: %d / %d" % (visible, len(in_taiwan) or 1))