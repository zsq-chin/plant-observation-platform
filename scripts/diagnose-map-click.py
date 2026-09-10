#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""诊断手机地图点击：收集 renderjs 日志并尝试多坐标点击。"""
import json
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(args=["--no-sandbox"])
    page = b.new_page(viewport={"width": 412, "height": 915})
    msgs = []
    page.on("console", lambda m: msgs.append(m.type + ": " + m.text[:200]))
    page.goto("http://127.0.0.1/jingxuan/app/#/pages/map/index", wait_until="load")
    page.wait_for_timeout(6000)
    box = page.locator(".china-map").bounding_box()
    print("map box:", json.dumps(box))
    points = [(0.5, 0.45), (0.35, 0.6), (0.62, 0.62), (0.5, 0.3), (0.45, 0.75), (0.55, 0.55), (0.4, 0.5)]
    for fx, fy in points:
        page.mouse.click(box["x"] + box["width"] * fx, box["y"] + box["height"] * fy)
        page.wait_for_timeout(600)
        head = page.locator(".panel__head").count()
        print("click %.2f/%.2f -> panel__head=%d" % (fx, fy, head))
        if head:
            break
    print("面板文本:", page.locator(".panel").inner_text().replace(chr(10), " ")[:120])
    print("事件总线可用:", page.evaluate("() => typeof uni !== 'undefined' && typeof uni.$emit === 'function'"))
    print("console 日志:")
    for m in msgs[-20:]:
        print("  ", m)
    b.close()