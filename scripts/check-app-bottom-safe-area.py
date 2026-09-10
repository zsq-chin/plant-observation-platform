# -*- coding: utf-8 -*-
"""滚动到底部，检查内容是否被固定底部导航遮挡。"""
import json
from playwright.sync_api import sync_playwright

APP = "http://10.120.46.175/jingxuan/app/"
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    i = page.locator("uni-input input")
    i.nth(0).fill("stu1")
    i.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2500)

    for path in ("pages/gallery/index", "pages/my-observations/index", "pages/home/index"):
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(2600)
        page.evaluate("""() => {
            const sc = document.querySelector('uni-page-wrapper') || window;
            window.scrollTo(0, document.body.scrollHeight);
            document.querySelectorAll('uni-scroll-view, uni-page-body').forEach(e => e.scrollTop = e.scrollHeight);
        }""")
        page.wait_for_timeout(1200)
        page.mouse.wheel(0, 4000)
        page.wait_for_timeout(1200)
        m = page.evaluate("""() => {
            const bar = document.querySelector('.uni-tabbar');
            const barTop = bar ? bar.getBoundingClientRect().top : window.innerHeight;
            const lift = document.querySelector('.lift') || document.querySelector('uni-page-body');
            const kids = lift ? Array.from(lift.children) : [];
            const last = kids.length ? kids[kids.length - 1].getBoundingClientRect() : null;
            return {
              barTop: Math.round(barTop),
              lastTag: last ? kids[kids.length - 1].tagName + '.' + (kids[kids.length - 1].className || '').toString().slice(0,30) : null,
              lastBottom: last ? Math.round(last.bottom) : null,
              hiddenBy: last ? Math.round(last.bottom - barTop) : null,
              scrollY: Math.round(window.scrollY),
              docH: document.body.scrollHeight,
            };
        }""")
        print("---", path)
        print(json.dumps(m, ensure_ascii=False))
    ctx.close(); b.close()
