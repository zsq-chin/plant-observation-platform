# -*- coding: utf-8 -*-
"""用真实点击（底部 Tab）切换页面，检查是否会触发控制台错误。"""
import json
from playwright.sync_api import sync_playwright

APP = "http://10.120.46.175/jingxuan/app/"
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    errs = []
    page.on("console", lambda m: errs.append(m.text[:180]) if m.type == "error" else None)
    page.on("pageerror", lambda e: errs.append("pageerror: " + str(e)[:180]))
    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    i = page.locator("uni-input input")
    i.nth(0).fill("stu1"); i.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2500)
    print("登录后:", page.url, "错误数:", len(errs))

    tabs = ["首页", "展廊", "采集", "我的植物", "我的"]
    for name in tabs:
        before = len(errs)
        page.locator(".uni-tabbar__label", has_text=name).first.click(timeout=5000)
        page.wait_for_timeout(2200)
        print("  点击 %-6s → %-46s 新增错误 %d" % (name, page.url.split("jingxuan")[-1], len(errs) - before))

    # 页面内跳转（地图、详情）也试一遍
    page.goto(APP + "#/pages/home/index")
    page.wait_for_timeout(1500)
    before = len(errs)
    page.locator(".tile", has_text="全国地图").first.click()
    page.wait_for_timeout(3000)
    print("  点击 全国地图 → %s 新增错误 %d" % (page.url.split("jingxuan")[-1], len(errs) - before))
    print("\n全部错误:", json.dumps(errs[:5], ensure_ascii=False))
    ctx.close(); b.close()
