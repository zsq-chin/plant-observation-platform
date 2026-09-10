# -*- coding: utf-8 -*-
"""布局体检：溢出、遮挡、可见性（无法肉眼看图，用几何量测代替）。"""
import json
from playwright.sync_api import sync_playwright

APP = "http://10.120.46.175/jingxuan/app/"
BASE = "http://10.120.46.175/jingxuan"

with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    inp = page.locator("uni-input input")
    inp.nth(0).fill("stu1")
    inp.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2600)

    for path in ("pages/home/index", "pages/gallery/index", "pages/my-observations/index", "pages/profile/index"):
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(2400)
        m = page.evaluate("""() => {
          const vw = window.innerWidth, vh = window.innerHeight;
          const hero = document.querySelector('.hero');
          const lift = document.querySelector('.lift');
          const tabbar = document.querySelector('.uni-tabbar') || document.querySelector('uni-tabbar');
          const pageEl = document.querySelector('uni-page-wrapper, .page');
          const bodyPadBottom = pageEl ? getComputedStyle(pageEl).paddingBottom : '';
          const overflow = [];
          document.querySelectorAll('uni-view, uni-image, uni-button, .card, .tile, .pcard').forEach(el => {
            const r = el.getBoundingClientRect();
            if (r.width > 0 && (r.right > vw + 1 || r.left < -1)) {
              const cls = (el.className || '').toString().slice(0, 40);
              if (overflow.length < 5) overflow.push({
                cls, left: Math.round(r.left), right: Math.round(r.right) });
            }
          });
          return {
            vw, vh,
            heroH: hero ? Math.round(hero.getBoundingClientRect().height) : 0,
            liftTop: lift ? Math.round(lift.getBoundingClientRect().top) : 0,
            firstCardTop: lift && lift.firstElementChild ? Math.round(lift.firstElementChild.getBoundingClientRect().top) : 0,
            tabbarH: tabbar ? Math.round(tabbar.getBoundingClientRect().height) : 0,
            tabbarTop: tabbar ? Math.round(tabbar.getBoundingClientRect().top) : 0,
            tabbarPos: tabbar ? getComputedStyle(tabbar).position : '',
            bodyPadBottom,
            bodyScrollW: document.body.scrollWidth,
            overflow,
          };
        }""")
        print("---", path)
        print(json.dumps(m, ensure_ascii=False))
        assert m["bodyScrollW"] <= m["vw"] + 1, "横向溢出: %s" % path
        if m["tabbarH"]:
            assert m["tabbarTop"] + m["tabbarH"] <= m["vh"] + 1, "底部导航超屏: %s" % path
    ctx.close()

    # 教师端侧边栏：新入口是否可见
    ctx2 = b.new_context(viewport={"width": 1440, "height": 900})
    page2 = ctx2.new_page()
    page2.goto(BASE + "/login", wait_until="domcontentloaded")
    page2.wait_for_timeout(1200)
    page2.fill('input[placeholder="学号 / 用户名"]', "tea1")
    page2.fill('input[placeholder="密码"]', "admin123")
    page2.click(".login-btn")
    page2.wait_for_timeout(3000)
    page2.goto(BASE + "/teacher/dashboard", wait_until="domcontentloaded")
    page2.wait_for_timeout(2200)
    side = page2.evaluate("""() => {
        const aside = document.querySelector('.workspace-layout__sidebar');
        const links = Array.from(document.querySelectorAll('.teacher-layout__link'));
        const note = document.querySelector('.workspace-layout__section-note');
        const ar = aside.getBoundingClientRect();
        return {
          aside: { w: Math.round(ar.width), h: Math.round(ar.height) },
          links: links.map(a => {
            const r = a.getBoundingClientRect();
            return { text: a.innerText.trim(), top: Math.round(r.top), bottom: Math.round(r.bottom),
                     h: Math.round(r.height), visible: r.height > 0 && r.width > 0,
                     color: getComputedStyle(a).color };
          }),
          noteTop: note ? Math.round(note.getBoundingClientRect().top) : null,
          label: (document.querySelector('.teacher-layout__aside-label') || {}).innerText,
        };
    }""")
    print("--- 教师端侧边栏")
    print(json.dumps(side, ensure_ascii=False, indent=1))
    ctx2.close()
    b.close()
print("\n布局体检通过")
