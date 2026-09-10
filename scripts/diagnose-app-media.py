# -*- coding: utf-8 -*-
"""诊断：学生端 App（H5 部署）图片显示链路 —— 修正选择器版。"""
import json
import sys
import urllib.request

BASE = "http://10.120.46.175/jingxuan"
APP = BASE + "/app/"

from playwright.sync_api import sync_playwright

net = []
errors = []
report = {}

with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    page.on("response", lambda r: net.append([r.status, r.headers.get("content-type", ""), r.url])
            if ("/media/" in r.url or "/uploads/" in r.url or "placeholder" in r.url or "geo/" in r.url) else None)
    page.on("console", lambda m: errors.append(m.type + ": " + m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: errors.append("pageerror: " + str(e)))

    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1500)

    inputs = page.locator("uni-input input")
    print("输入框数量:", inputs.count())
    inputs.nth(0).fill("stu1")
    inputs.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(3000)
    print("登录后 URL:", page.url)
    print("登录后文本:", page.inner_text("body")[:120].replace("\n", " | "))

    def dump(tag):
        info = page.evaluate("""() => {
          const imgs = Array.from(document.querySelectorAll('uni-image')).map(u => {
            const r = u.getBoundingClientRect();
            const inner = u.querySelector('div');
            const img = u.querySelector('img');
            return {
              w: Math.round(r.width), h: Math.round(r.height),
              src: (inner && getComputedStyle(inner).backgroundImage) || (img && img.src) || '',
              nw: img ? img.naturalWidth : null,
            };
          });
          const rawImgs = Array.from(document.querySelectorAll('img')).map(i => ({src: i.src, nw: i.naturalWidth}));
          return {uni: imgs, raw: rawImgs};
        }""")
        report[tag] = info
        print("--- %s (%s) ---" % (tag, page.url))
        print("文本:", page.inner_text("body")[:200].replace("\n", " | "))
        for it in info["uni"][:6]:
            print("   uni-image %sx%s src=%s" % (it["w"], it["h"], str(it["src"])[:120]))
        for it in info["raw"][:6]:
            print("   img nw=%s src=%s" % (it["nw"], str(it["src"])[:120]))

    for path, tag in (("pages/home/index", "首页"),
                      ("pages/my-observations/index", "我的植物"),
                      ("pages/gallery/index", "展廊"),
                      ("pages/map/index", "全国植物地图")):
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(3200)
        dump(tag)

    # 详情页（自己的观察）
    page.goto(APP + "#/pages/observation-detail/index?id=2097872264876486658")
    page.wait_for_timeout(3200)
    dump("观察详情(自有)")

    ctx.close()
    b.close()

print()
print("=== /media/ 与 placeholder 网络请求 ===")
seen = set()
for st, ct, u in net:
    k = (st, ct, u)
    if k in seen:
        continue
    seen.add(k)
    print(" %s  %-24s %s" % (st, ct, u[:150]))
if not net:
    print(" (无)")

print()
print("=== 控制台错误 ===")
for e in errors[:20]:
    print(" ", e[:180])
if not errors:
    print(" (无)")
