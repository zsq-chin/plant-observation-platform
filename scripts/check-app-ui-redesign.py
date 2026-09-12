# -*- coding: utf-8 -*-
"""校验：学生端改版（视觉系统 / 底部图标）与地图入口跳转；教师端展廊入口。"""
import sys

BASE = "http://10.120.46.175/jingxuan"
APP = BASE + "/app/"

from playwright.sync_api import sync_playwright

failures = []


def check(ok, message):
    print(("  [PASS] " if ok else "  [FAIL] ") + message)
    if not ok:
        failures.append(message)


with sync_playwright() as p:
    browser = p.chromium.launch()

    # ---------------- 学生端 ----------------
    print("== 学生端 App ==")
    ctx = browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    errors = []
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: errors.append(str(e)))

    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    inputs = page.locator("uni-input input")
    inputs.nth(0).fill("stu1")
    inputs.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2600)
    check("pages/home/index" in page.url, "登录后进入首页（%s）" % page.url)

    # 底部图标
    icons = page.evaluate("""() => Array.from(document.querySelectorAll('uni-tabbar img')).map(i => ({
        src: i.src.replace(location.origin, ''), nw: i.naturalWidth }))""")
    print("  底部图标:", [i["src"].split("/")[-1] for i in icons])
    check(len(icons) == 5, "底部导航有 5 个图标（实际 %d）" % len(icons))
    check(all(i["nw"] > 0 for i in icons), "全部图标加载成功（naturalWidth > 0）")
    check(len({i["src"] for i in icons}) == 5, "5 个图标各不相同（不再是同一个 logo）")

    # 首页视觉
    hero = page.evaluate("""() => {
        const h = document.querySelector('uni-view.hero, .hero');
        const tiles = document.querySelectorAll('.quick__item').length;
        const stats = document.querySelectorAll('.stat').length;
        const cs = h ? getComputedStyle(h) : null;
        const lift = document.querySelector('.lift');
        const quickFirst = !!(lift && lift.firstElementChild && lift.firstElementChild.className.toString().includes('quick'));
        return { hero: !!h, bg: cs ? cs.backgroundImage.slice(0, 40) : '', tiles, stats, quickFirst };
    }""")
    print("  首页:", hero)
    check(hero["hero"], "首页有渐变头图")
    check("gradient" in hero["bg"], "头图使用渐变背景")
    check(hero["stats"] == 4, "首页 4 个统计块（实际 %d）" % hero["stats"])
    check(hero["tiles"] == 6, "首页 6 个快捷入口（实际 %d）" % hero["tiles"])
    check(hero["quickFirst"], "快捷入口位于内容区最上方")

    # 关键：点「全国地图」必须能跳转（原实现用 switchTab 对非 tabBar 页面静默失败）
    page.locator(".quick__item", has_text="全国地图").first.click()
    page.wait_for_timeout(3200)
    on_map = "pages/map/index" in page.url
    check(on_map, "点击首页「全国地图」跳转到地图页（%s）" % page.url)
    if on_map:
        canvas = page.evaluate("""() => {
            const c = document.querySelector('uni-canvas canvas, canvas');
            return c ? { w: c.clientWidth, h: c.clientHeight } : null;
        }""")
        print("  地图 canvas:", canvas)
        check(bool(canvas) and canvas["w"] > 100 and canvas["h"] > 100, "地图画布已渲染且有尺寸")

    # 各页面渲染与报错
    pages = [
        ("pages/home/index", "首页"),
        ("pages/gallery/index", "展廊"),
        ("pages/my-observations/index", "我的植物"),
        ("pages/profile/index", "我的"),
        ("pages/notification/index", "消息通知"),
        ("pages/suggestion/index", "新物种建议"),
    ]
    for path, label in pages:
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(2200)
        text = page.inner_text("body")
        hero_ok = page.evaluate("""() => !!document.querySelector('.hero')""")
        print("  %-8s 文本长度=%d 头图=%s" % (label, len(text), hero_ok))
        check(len(text.strip()) > 0, "%s 有内容渲染" % label)

    for path in ("pages/capture/index", "pages/observation-edit/index?id=2097872264876486658",
                 "pages/observation-detail/index?public=1&id=2097872264876486658"):
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(2500)
        check(len(page.inner_text("body").strip()) > 0, "%s 有内容渲染" % path.split("?")[0])

    # 详情页图片仍正常
    media = page.evaluate("""() => Array.from(document.querySelectorAll('uni-image')).map(u => {
        const inner = u.querySelector('div');
        const bg = inner ? getComputedStyle(inner).backgroundImage : '';
        return bg && bg !== 'none' ? bg : '';
    }).filter(Boolean)""")
    check(len(media) > 0, "详情页图片正常显示（%d 张）" % len(media))
    real_errors = [e for e in errors if "favicon" not in e.lower()]
    print("  控制台错误:", real_errors[:3] if real_errors else "无")
    check(not real_errors, "学生端无控制台错误")
    ctx.close()

    # ---------------- 教师端 ----------------
    print("== 教师端 ==")
    ctx2 = browser.new_context(viewport={"width": 1440, "height": 900})
    page2 = ctx2.new_page()
    page2.goto(BASE + "/login", wait_until="domcontentloaded")
    page2.wait_for_timeout(1500)
    try:
        page2.fill('input[placeholder="学号 / 用户名"]', "tea1")
        page2.fill('input[placeholder="密码"]', "admin123")
        page2.click(".login-btn")
        page2.wait_for_timeout(3000)
    except Exception as e:
        print("  登录表单定位失败:", str(e)[:120])
    page2.goto(BASE + "/teacher/dashboard", wait_until="domcontentloaded")
    page2.wait_for_timeout(2500)
    links = page2.evaluate("""() => Array.from(document.querySelectorAll('.teacher-layout__link')).map(a => ({
        href: a.getAttribute('href'), text: a.innerText.trim(), target: a.getAttribute('target') }))""")
    print("  教师端外链:", links)
    check(any("/plant/gallery" in (l["href"] or "") for l in links), "教师端存在「植物展廊」入口")
    check(all(l["target"] == "_blank" for l in links) and links, "入口在新标签页打开（不打断评审）")
    ctx2.close()
    browser.close()

print()
if failures:
    print("失败项 %d 个：" % len(failures))
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("学生端改版与入口跳转校验全部通过")
