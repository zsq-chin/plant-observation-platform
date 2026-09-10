#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实浏览器验证：PC 3D 地图（台湾省）、手机 H5 地图（点击）、图片显示链路。

用法： python -X utf8 scripts/verify-ui-playwright.py --base http://127.0.0.1
"""
import argparse
import json
import os
import sys
from playwright.sync_api import sync_playwright

FAIL = []


def check(cond, msg):
    print(("[PASS] " if cond else "[FAIL] ") + msg)
    if not cond:
        FAIL.append(msg)


def shot(page, name, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, name)
    page.screenshot(path=path, full_page=False)
    return path


def check_pc_map(page, base, out_dir):
    page.goto(base + "/jingxuan/plant/map", wait_until="load")
    page.wait_for_timeout(5000)
    info = page.evaluate("""() => {
      const map = window.__plantMap;
      const canvas = document.querySelector('.map-canvas canvas');
      if (!map) return { hasMap: false };
      const src = map.querySourceFeatures('provinces') || [];
      const codes = src.map((f) => String((f.properties || {}).adcode || f.id || ''));
      const rendered = map.queryRenderedFeatures({ layers: ['province-fill'] }) || [];
      const rcodes = rendered.map((f) => String((f.properties || {}).adcode || ''));
      return {
        hasMap: true,
        canvas: !!canvas,
        canvasW: canvas ? canvas.width : 0,
        canvasH: canvas ? canvas.height : 0,
        srcCount: src.length,
        srcHas710000: codes.includes('710000'),
        renderedCount: rendered.length,
        renderedHas710000: rcodes.includes('710000'),
        labels: document.querySelectorAll('.plant-province-label').length,
        hasTaiwanLabel: Array.from(document.querySelectorAll('.plant-province-label')).some((el) => el.textContent.includes('台湾')),
      };
    }""")
    print("  PC 地图信息:", json.dumps(info, ensure_ascii=False))
    path = shot(page, "pc-map.png", out_dir)
    print("  截图:", path)
    check(info.get("hasMap") and info.get("canvas"), "PC 地图已初始化并渲染 canvas")
    check(info.get("srcHas710000"), "地图数据源包含台湾省 710000")
    check(info.get("renderedHas710000"), "台湾省在当前视野内被渲染（queryRenderedFeatures）")
    check(info.get("hasTaiwanLabel"), "台湾省名称标签存在")
    # 点击台湾（约 121.0E, 23.7N）
    point = page.evaluate("""() => {
      const map = window.__plantMap;
      if (!map) return null;
      const p = map.project([121.0, 23.7]);
      return { x: p.x, y: p.y };
    }""")
    if point:
        box = page.locator(".map-canvas").bounding_box()
        page.mouse.click(box["x"] + point["x"], box["y"] + point["y"])
        page.wait_for_timeout(3000)
        side = page.locator(".map-side").inner_text() if page.locator(".map-side").count() else ""
        print("  点击台湾后面板文本:", side.replace(chr(10), " ")[:80])
        check("台湾" in side, "点击台湾省打开省份面板（provinceCode=710000 链路）")
        shot(page, "pc-map-taiwan-panel.png", out_dir)


def check_mobile_map(page, base, out_dir):
    page.set_viewport_size({"width": 412, "height": 915})
    page.goto(base + "/jingxuan/app/#/pages/map/index", wait_until="load")
    page.wait_for_timeout(6000)
    info = page.evaluate("""() => {
      const canvas = document.querySelector('.china-map canvas');
      const fallback = document.body.innerText.includes('省份列表');
      return { hasCanvas: !!canvas, w: canvas ? canvas.width : 0, h: canvas ? canvas.height : 0, fallback };
    }""")
    print("  手机地图信息:", json.dumps(info, ensure_ascii=False))
    shot(page, "mobile-map.png", out_dir)
    check(info.get("hasCanvas") and info.get("w", 0) > 0, "手机地图渲染出 ECharts canvas")
    check(not info.get("fallback"), "未落入省份列表降级分支")
    # 点击网格尝试命中省份（用页面文本判断，避免依赖会被改版调整的类名）
    box = page.locator(".china-map").bounding_box()
    clicked = False
    text = ""
    for fx, fy in ((0.5, 0.45), (0.35, 0.6), (0.62, 0.62), (0.5, 0.3), (0.45, 0.75)):
        page.mouse.click(box["x"] + box["width"] * fx, box["y"] + box["height"] * fy)
        page.wait_for_timeout(1400)
        text = page.inner_text("body")
        if "收起" in text and "条观察" in text:
            clicked = True
            break
    print("  点击后面板:", text.replace(chr(10), " ")[:140])
    check(clicked, "手机地图点击省份触发 select（面板展开）")
    shot(page, "mobile-map-selected.png", out_dir)


def check_images(page, base, out_dir):
    page.set_viewport_size({"width": 1280, "height": 900})
    page.goto(base + "/jingxuan/plant/gallery", wait_until="load")
    page.wait_for_timeout(4000)
    stats = page.evaluate("""() => {
      const imgs = Array.from(document.querySelectorAll('.plant-work-card__cover img, .obs-card__cover img'));
      return {
        total: imgs.length,
        loaded: imgs.filter((i) => i.complete && i.naturalWidth > 0).length,
        srcs: imgs.slice(0, 3).map((i) => i.getAttribute('src')),
        broken: imgs.filter((i) => i.complete && i.naturalWidth === 0).map((i) => i.getAttribute('src')),
      };
    }""")
    print("  展廊图片:", json.dumps(stats, ensure_ascii=False))
    shot(page, "gallery-images.png", out_dir)
    check(stats["total"] > 0, "展廊存在作品卡片图片")
    check(stats["loaded"] == stats["total"] and stats["total"] > 0, "全部图片加载成功（naturalWidth>0）")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1")
    parser.add_argument("--out", default="tmp-ui-check")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--no-sandbox"])
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        errors = []
        page.on("pageerror", lambda exc: errors.append(str(exc)))
        print("== PC 3D 地图 ==")
        check_pc_map(page, base, args.out)
        print("== 手机 H5 地图 ==")
        check_mobile_map(page, base, args.out)
        print("== 图片显示 ==")
        check_images(page, base, args.out)
        if errors:
            print("== 页面 JS 错误 ==")
            for err in errors[:6]:
                print("  -", err[:180])
        browser.close()
    print("")
    if FAIL:
        print("失败项 %d:" % len(FAIL))
        for item in FAIL:
            print("  -", item)
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())