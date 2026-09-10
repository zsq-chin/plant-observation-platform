# -*- coding: utf-8 -*-
"""校验：手机端图片链路（绝对地址）与 PC/App 地图边界数据版本化。

判定标准：
1. App 内所有 /media/plants/ 请求必须是 http(s)://host/media/plants/...（根路径），
   状态 200 且 Content-Type 为 image/*；出现 text/html 即落入 SPA 回退，判失败。
2. App 内至少一个 <uni-image> 真正挂上了图片。
3. PC 与 App 的地图边界请求都必须带 ?v=<指纹>，且边界数据含 34 个省级要素与 710000。
4. PC 地图确实渲染出台湾省（queryRenderedFeatures 命中 710000）。
"""
import json
import sys

BASE = "http://10.120.46.175/jingxuan"
APP = BASE + "/app/"
PC_MAP = BASE + "/plant/map"

from playwright.sync_api import sync_playwright

failures = []
notes = []


def check(ok, message):
    print(("  [PASS] " if ok else "  [FAIL] ") + message)
    if not ok:
        failures.append(message)


with sync_playwright() as p:
    browser = p.chromium.launch()

    # ---------------- 手机端 ----------------
    print("== 手机端 App 图片链路 ==")
    ctx = browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    media, geo_app = [], []
    page.on("response", lambda r: media.append([r.status, r.headers.get("content-type", ""), r.url])
            if "/media/plants/" in r.url else None)
    page.on("response", lambda r: geo_app.append(r.url) if "china-provinces.json" in r.url else None)
    page.on("requestfailed", lambda r: media.append(["FAILED", "", r.url]) if "/media/plants/" in r.url else None)

    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    inputs = page.locator("uni-input input")
    inputs.nth(0).fill("stu1")
    inputs.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2500)

    for path in ("pages/home/index", "pages/gallery/index", "pages/map/index"):
        page.goto(APP + "#/" + path)
        page.wait_for_timeout(3200)
    page.goto(APP + "#/pages/observation-detail/index?id=2097872264876486658")
    page.wait_for_timeout(3200)

    imgs = page.evaluate("""() => Array.from(document.querySelectorAll('uni-image')).map(u => {
        const inner = u.querySelector('div');
        const img = u.querySelector('img');
        const bg = inner ? getComputedStyle(inner).backgroundImage : '';
        return { bg: bg && bg !== 'none' ? bg : '', src: img ? img.src : '', nw: img ? img.naturalWidth : 0 };
    })""")
    ctx.close()

    print("  图片请求样例:")
    for st, ct, u in media[:5]:
        print("    %s %-22s %s" % (st, ct, u))
    check(len(media) > 0, "手机端确实发起了 /media/plants/ 图片请求（%d 次）" % len(media))
    bad_path = [u for _, _, u in media if "/jingxuan/app/media/plants/" in u]
    check(not bad_path, "没有请求被拼成 /jingxuan/app/media/plants/...（错误前缀 %d 次）" % len(bad_path))
    html_resp = [(st, ct) for st, ct, _ in media if "text/html" in (ct or "")]
    check(not html_resp, "没有任何图片请求回退到 SPA 的 text/html（%d 次）" % len(html_resp))
    check(all(st == 200 and (ct or "").startswith("image/") for st, ct, _ in media),
          "全部图片请求 200 且 Content-Type 为 image/*")
    loaded = [i for i in imgs if i["bg"] or i["nw"] > 0]
    check(len(loaded) > 0, "至少一张 <uni-image> 真正显示了图片（%d/%d）" % (len(loaded), len(imgs)))
    if loaded:
        notes.append("App 图片实际地址样例: " + (loaded[0]["bg"] or loaded[0]["src"])[:130])
    app_geo = [u for u in geo_app if "/app/static/geo/" in u]
    check(len(app_geo) > 0 and all("?v=" in u for u in app_geo),
          "App 地图边界请求带版本指纹（%s）" % (app_geo[0] if app_geo else "无请求"))

    # ---------------- PC 端 ----------------
    print("== PC 3D 地图 ==")
    ctx2 = browser.new_context(viewport={"width": 1440, "height": 900})
    page2 = ctx2.new_page()
    geo_pc = []
    page2.on("response", lambda r: geo_pc.append(r.url) if "china-provinces.json" in r.url else None)
    page2.goto(PC_MAP, wait_until="domcontentloaded")
    page2.wait_for_timeout(10000)
    pc_geo = [u for u in geo_pc if "/geo/" in u]
    print("  PC 边界请求:", pc_geo[:1])
    check(len(pc_geo) > 0 and all("?v=" in u for u in pc_geo), "PC 地图边界请求带版本指纹")

    if pc_geo:
        data = page2.evaluate("""async (url) => {
            const r = await fetch(url);
            const j = await r.json();
            const ids = j.features.map(f => String((f.properties && f.properties.adcode) || f.id || ''));
            return { count: j.features.length, hasTaiwan: ids.includes('710000'), type: (j.features.find(f => String((f.properties && f.properties.adcode) || f.id) === '710000') || {}).geometry ? (j.features.find(f => String((f.properties && f.properties.adcode) || f.id) === '710000')).geometry.type : null };
        }""", pc_geo[0])
        print("  边界数据:", json.dumps(data, ensure_ascii=False))
        check(data["count"] == 34, "PC 边界要素数 = 34（实际 %s）" % data["count"])
        check(data["hasTaiwan"], "PC 边界数据包含台湾省 710000")
        check(data["type"] == "MultiPolygon", "台湾几何类型为 MultiPolygon（实际 %s）" % data["type"])

    render = page2.evaluate("""() => {
        const m = window.__plantMap;
        if (!m || !m.queryRenderedFeatures) return null;
        const rendered = m.queryRenderedFeatures();
        const fill = rendered.filter(f => f.layer.id === 'province-fill');
        const src = m.querySourceFeatures('provinces');
        return {
          layers: m.getStyle().layers.map(l => l.id),
          fillRendered: fill.length,
          renderedTaiwan: fill.some(f => String(f.id) === '710000'),
          srcTaiwan: src.some(f => String(f.id) === '710000' || String(f.properties && f.properties.adcode) === '710000'),
        };
    }""")
    ctx2.close()
    browser.close()

    if render:
        print("  PC 渲染信息:", json.dumps(render, ensure_ascii=False)[:300])
        check(render["renderedTaiwan"], "PC 地图已渲染台湾省（province-fill 命中 710000）")
        check('province-fill' in render["layers"], "province-fill 图层未被 MapLibre 丢弃")
    else:
        check(False, "PC 页面未暴露 __plantMap 实例")


    # ---------------- 老客户端缓存污染回归 ----------------
    print("== 缓存污染回归（模拟“曾经缓存过 33 省旧边界”的浏览器）==")
    import json as _json
    old_geo = _json.loads(open("frontend/public/geo/china-provinces.json", encoding="utf-8").read())
    old_geo["features"] = [x for x in old_geo["features"]
                           if str((x.get("properties") or {}).get("adcode") or x.get("id")) != "710000"]
    old_body = _json.dumps(old_geo)
    poisoned_hits = []

    def poison(route):
        url = route.request.url
        if "?v=" in url:
            route.continue_()
            return
        poisoned_hits.append(url)
        route.fulfill(status=200, content_type="application/json", body=old_body,
                      headers={"Cache-Control": "public, immutable, max-age=31536000"})

    browser3 = p.chromium.launch()
    ctx3 = browser3.new_context(viewport={"width": 1440, "height": 900})
    page3 = ctx3.new_page()
    page3.route("**/china-provinces.json*", poison)
    page3.goto(PC_MAP, wait_until="domcontentloaded")
    page3.wait_for_timeout(10000)
    render2 = page3.evaluate("""() => {
        const m = window.__plantMap;
        if (!m || !m.queryRenderedFeatures) return null;
        const fill = m.queryRenderedFeatures().filter(f => f.layer.id === 'province-fill');
        return { renderedTaiwan: fill.some(f => String(f.id) === '710000') };
    }""")
    ctx3.close()
    browser3.close()
    print("  旧地址（无 ?v=）被请求次数:", len(poisoned_hits))
    check(not poisoned_hits, "地图不再请求不带版本指纹的旧地址（旧缓存无法再被命中）")
    check(bool(render2) and render2["renderedTaiwan"], "即使浏览器存有 33 省旧缓存，台湾省依然渲染")

print()
print()
for n in notes:
    print("  说明:", n)
if failures:
    print("\n失败项 %d 个：" % len(failures))
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("\n手机端图片与地图边界数据校验全部通过")