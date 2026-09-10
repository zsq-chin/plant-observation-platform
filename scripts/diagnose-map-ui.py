#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""诊断：PC 地图渲染状态与手机 H5 地图初始化失败原因。"""
import json
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1"

with sync_playwright() as p:
    browser = p.chromium.launch(args=["--no-sandbox"])
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    console_msgs = []
    page.on("console", lambda m: console_msgs.append(m.type + ": " + m.text[:200]))
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)[:300]))

    print("=== PC 地图诊断 ===")
    page.goto(BASE + "/jingxuan/plant/map", wait_until="load")
    page.wait_for_timeout(6000)
    diag = page.evaluate("""() => {
      const map = window.__plantMap;
      if (!map) return { hasMap: false };
      const style = map.getStyle();
      const src = map.getSource('provinces');
      const data = src && src._data ? src._data : null;
      const bounds = map.getBounds();
      return {
        hasMap: true,
        styleLoaded: map.isStyleLoaded(),
        layers: (style.layers || []).map((l) => l.id),
        sourceFeatures: data && data.features ? data.features.length : null,
        sourceHas710000: data && data.features ? data.features.some((f) => String((f.properties||{}).adcode) === '710000') : null,
        bounds: [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()],
        zoom: map.getZoom(),
        allRendered: map.queryRenderedFeatures().length,
        fillRendered: map.queryRenderedFeatures({ layers: ['province-fill'] }).length,
        extrusionRendered: map.queryRenderedFeatures({ layers: ['province-extrusion'] }).length,
        borderRendered: map.queryRenderedFeatures({ layers: ['province-border'] }).length,
        filtered: map.queryRenderedFeatures({ layers: ['province-fill'] }).map((f) => String((f.properties||{}).adcode)).slice(0, 40),
      };
    }""")
    print(json.dumps(diag, ensure_ascii=False, indent=1))
    print("页面错误:", errors[:3])

    print("")
    print("=== 手机 H5 地图诊断 ===")
    page2 = browser.new_page(viewport={"width": 412, "height": 915})
    msgs2 = []
    page2.on("console", lambda m: msgs2.append(m.type + ": " + m.text[:220]))
    errs2 = []
    page2.on("pageerror", lambda e: errs2.append(str(e)[:300]))
    page2.goto(BASE + "/jingxuan/app/#/pages/map/index", wait_until="load")
    page2.wait_for_timeout(7000)
    mdiag = page2.evaluate("""() => {
      const el = document.getElementById('china-map-canvas');
      return {
        hasElement: !!el,
        elSize: el ? [el.clientWidth, el.clientHeight] : null,
        hasCanvasChild: el ? !!el.querySelector('canvas') : false,
        bodyText: document.body.innerText.slice(0, 200),
        echartsGlobal: typeof window.echarts,
      };
    }""")
    print(json.dumps(mdiag, ensure_ascii=False, indent=1))
    print("console 消息:")
    for m in msgs2[-12:]:
        print("  ", m)
    print("页面错误:")
    for e in errs2[:5]:
        print("  ", e)
    browser.close()