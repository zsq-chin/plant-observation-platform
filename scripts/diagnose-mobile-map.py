#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""诊断手机 H5 地图组件渲染情况。"""
import json
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1"

with sync_playwright() as p:
    browser = p.chromium.launch(args=["--no-sandbox"])
    page = browser.new_page(viewport={"width": 412, "height": 915})
    msgs = []
    page.on("console", lambda m: msgs.append(m.type + ": " + m.text[:200]))
    errs = []
    page.on("pageerror", lambda e: errs.append(str(e)[:300]))
    page.goto(BASE + "/jingxuan/app/#/pages/map/index", wait_until="load")
    page.wait_for_timeout(7000)
    info = page.evaluate("""() => {
      const byId = document.getElementById('china-map-canvas');
      const anyCanvas = document.querySelectorAll('canvas').length;
      const root = document.querySelector('.china-map');
      const uniViews = document.querySelectorAll('uni-view').length;
      const idsInRoot = root ? Array.from(root.querySelectorAll('[id]')).map((el) => el.tagName + '#' + el.id) : [];
      return {
        byId: !!byId,
        byIdTag: byId ? byId.tagName : null,
        byIdSize: byId ? [byId.clientWidth, byId.clientHeight] : null,
        anyCanvas,
        hasRoot: !!root,
        rootHtml: root ? root.outerHTML.slice(0, 400) : null,
        uniViews,
        idsInRoot,
      };
    }""")
    print(json.dumps(info, ensure_ascii=False, indent=1))
    print("console:")
    for m in msgs[-15:]:
        print("  ", m)
    print("errors:")
    for e in errs[:5]:
        print("  ", e)
    browser.close()