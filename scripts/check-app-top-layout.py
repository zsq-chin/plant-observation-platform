# -*- coding: utf-8 -*-
"""校验：手机端首页顶部布局（快捷入口置顶、头图缩小、无遮挡）与实际跳转。"""
import json
import sys
from playwright.sync_api import sync_playwright

APP = "http://10.120.46.175/jingxuan/app/"
failures = []


def check(ok, message):
    print(("  [PASS] " if ok else "  [FAIL] ") + message)
    if not ok:
        failures.append(message)


with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    errs = []
    page.on("console", lambda m: errs.append(m.text[:140]) if m.type == "error" else None)
    page.on("pageerror", lambda e: errs.append(str(e)[:140]))
    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    i = page.locator("uni-input input")
    i.nth(0).fill("stu1"); i.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2800)

    m = page.evaluate("""() => {
        const hero = document.querySelector('.hero');
        const lift = document.querySelector('.lift');
        const quick = document.querySelector('.quick');
        const stats = document.querySelector('.stats');
        const secs = Array.from(document.querySelectorAll('.sec .sec__title')).map(s => s.innerText.trim());
        const kids = Array.from(lift.children).map(c => c.className.toString().split(' ')[0]);
        const hr = hero.getBoundingClientRect();
        const lr = lift.getBoundingClientRect();
        const qr = quick ? quick.getBoundingClientRect() : null;
        return {
            heroH: Math.round(hr.height),
            heroBottom: Math.round(hr.bottom),
            liftTop: Math.round(lr.top),
            quickTop: qr ? Math.round(qr.top) : null,
            quickH: qr ? Math.round(qr.height) : null,
            quickItems: document.querySelectorAll('.quick__item').length,
            statsTop: stats ? Math.round(stats.getBoundingClientRect().top) : null,
            liftChildren: kids,
            sections: secs,
            overlap: Math.round(hr.bottom - lr.top),
            viewport: window.innerHeight,
            pageScrollH: document.body.scrollHeight,
        };
    }""")
    print("  首页布局:", json.dumps(m, ensure_ascii=False, indent=1))

    check(m["heroH"] <= 120, "头图高度已压到 %dpx（≤120px）" % m["heroH"])
    check(m["overlap"] <= 0, "内容区与头图不再重叠（overlap=%d）" % m["overlap"])
    check(m["quickItems"] == 6, "快捷入口 6 项（实际 %d）" % m["quickItems"])
    check(m["liftChildren"] and m["liftChildren"][0] == "card", "快捷入口是内容区第一块（%s）" % (m["liftChildren"][:3],))
    check(m["quickTop"] is not None and m["statsTop"] is not None and m["quickTop"] < m["statsTop"],
          "快捷入口在统计块之上")
    check("最新优秀观察" in m["sections"], "优秀观察区块仍在（%s）" % m["sections"])

    # 快捷入口可直接跳转
    page.locator(".quick__item", has_text="全国地图").first.click()
    page.wait_for_timeout(4000)
    check("pages/map/index" in page.url, "点「全国地图」进入地图页")
    mm = page.evaluate("""() => {
        const s = document.querySelector('.summary');
        const map = document.querySelector('.china-map');
        const sr = s ? s.getBoundingClientRect() : null;
        const mr = map ? map.getBoundingClientRect() : null;
        return { summaryH: sr ? Math.round(sr.height) : null,
                 summaryBottom: sr ? Math.round(sr.bottom) : null,
                 mapTop: mr ? Math.round(mr.top) : null,
                 mapH: mr ? Math.round(mr.height) : null };
    }""")
    print("  地图页:", json.dumps(mm, ensure_ascii=False))
    check(mm["summaryH"] is not None and mm["summaryH"] <= 150, "地图页顶部块已压扁（%dpx）" % (mm["summaryH"] or 0))
    check(mm["mapTop"] is not None and mm["mapTop"] >= (mm["summaryBottom"] or 0), "地图未被顶部块遮挡")
    check(mm["mapH"] == 330, "地图高度仍为 330px（实际 %s）" % mm["mapH"])
    check(not errs, "无控制台错误（%s）" % (errs[:1] or "无"))

    # 其余页面回归
    for path, label in (("pages/gallery/index", "展廊"), ("pages/my-observations/index", "我的植物"),
                        ("pages/profile/index", "我的")):
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(2200)
        g = page.evaluate("""() => {
            const hero = document.querySelector('.hero');
            const lift = document.querySelector('.lift');
            const tabs = document.querySelector('.tabs-wrap');
            const anchor = tabs || lift;
            return { heroBottom: hero ? Math.round(hero.getBoundingClientRect().bottom) : null,
                     anchorTop: anchor ? Math.round(anchor.getBoundingClientRect().top) : null };
        }""")
        ok = g["heroBottom"] is None or g["anchorTop"] is None or g["anchorTop"] >= g["heroBottom"]
        check(ok, "%s 页内容不被头图遮挡（hero底=%s, 内容顶=%s）" % (label, g["heroBottom"], g["anchorTop"]))

    ctx.close(); b.close()

print()
if failures:
    print("失败项 %d 个：" % len(failures))
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("手机端顶部布局校验全部通过")
