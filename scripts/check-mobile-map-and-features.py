# -*- coding: utf-8 -*-
"""收尾校验：手机端地图可见性（含拖拽）、图标、我的植物封面、消息已读、地图兜底。"""
import json
import sys
import urllib.request

BASE = "http://10.120.46.175/jingxuan"
APP = BASE + "/app/"
API = "http://10.120.46.175"

from playwright.sync_api import sync_playwright

failures = []


def check(ok, message):
    print(("  [PASS] " if ok else "  [FAIL] ") + message)
    if not ok:
        failures.append(message)


def api(path, token=None, method="GET", body=None):
    req = urllib.request.Request(API + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(req, data, timeout=20) as r:
        return json.loads(r.read().decode())


PIXELS = """() => {
  const c = document.querySelector('.china-map canvas');
  if (!c) return { err: 'no canvas' };
  const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
  let opaque = 0, samples = 0;
  for (let i = 0; i < d.length; i += 4 * 53) { samples++; if (d[i + 3] > 10) opaque++; }
  return { samples, opaque, ratio: +(opaque / Math.max(1, samples)).toFixed(3) };
}"""

print("== 1. 手机端地图可见性（含手势拖拽）==")
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1200)
    i = page.locator("uni-input input")
    i.nth(0).fill("stu1")
    i.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2500)
    # 走用户真实路径：首页点「全国地图」（而不是直接打开地图 URL）——
    # 曾经的 DOM 归属冲突只在页面切换时触发，直接开 URL 测不出来
    console_errors = []
    page.on("console", lambda m: console_errors.append(m.text[:150]) if m.type == "error" else None)
    page.on("pageerror", lambda e: console_errors.append(str(e)[:150]))
    page.locator(".tile", has_text="全国地图").first.click()
    page.wait_for_timeout(6000)
    check("pages/map/index" in page.url, "首页点「全国地图」成功进入地图页")
    check(not console_errors, "切换过程中无控制台错误（%s）" % (console_errors[:1] or "无"))
    before = page.evaluate(PIXELS)
    print("  初始:", json.dumps(before, ensure_ascii=False))
    check(before.get("ratio", 0) > 0.10, "地图已真正绘制（非空白，实心像素占比 %.3f）" % before.get("ratio", 0))

    # 在地图上做一次滑动（真机上用户滚动页面就会这样操作）
    box = page.locator(".china-map").bounding_box()
    cx, cy = box["x"] + box["width"] / 2, box["y"] + box["height"] / 2
    page.mouse.move(cx, cy)
    page.mouse.down()
    for dx in range(0, 220, 20):
        page.mouse.move(cx - dx, cy - dx * 0.4)
        page.wait_for_timeout(30)
    page.mouse.up()
    page.wait_for_timeout(1500)
    after = page.evaluate(PIXELS)
    print("  拖拽后:", json.dumps(after, ensure_ascii=False))
    check(after.get("ratio", 0) > 0.10, "在地图上滑动后地图仍然可见（roam 已关闭，不会被拖走）")

    # 点击省份仍可用（网格试探，避免落点不在省份图形上）
    opened = False
    for fx, fy in ((0.5, 0.62), (0.38, 0.55), (0.6, 0.5), (0.45, 0.7), (0.55, 0.35), (0.3, 0.45)):
        page.mouse.click(box["x"] + box["width"] * fx, box["y"] + box["height"] * fy)
        page.wait_for_timeout(1400)
        body = page.inner_text("body")
        if "收起" in body and "条观察" in body:
            opened = True
            break
    check(opened, "拖拽后点击省份仍能打开面板")
    ctx.close()

    # 兜底：拦截边界数据请求，模拟加载失败
    print("== 2. 地图加载失败兜底 ==")
    ctx2 = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page2 = ctx2.new_page()
    page2.goto(APP, wait_until="networkidle")
    page2.wait_for_timeout(1200)
    j = page2.locator("uni-input input")
    j.nth(0).fill("stu1")
    j.nth(1).fill("admin123")
    page2.locator("uni-button").first.click()
    page2.wait_for_timeout(2500)
    page2.route("**/china-provinces.json*", lambda route: route.abort())
    page2.goto(APP + "#/pages/map/index")
    page2.wait_for_timeout(10000)
    fb = page2.inner_text("body")
    check("省份列表" in fb, "边界数据加载失败时展示省份列表兜底（不再空白）")
    check("重试地图" in fb, "兜底视图提供「重试地图」入口")
    ctx2.close()
    b.close()

print("== 3. 部署新鲜度（防止 nginx 用到旧镜像）==")
import os
local_assets = os.listdir("student-app/dist/build/h5/assets")
local_map = sorted(x for x in local_assets if x.startswith("pages-map-index.") and x.endswith(".js"))
out = os.popen('docker exec jingxuan-nginx-1 sh -c "ls /usr/share/nginx/html/jingxuan/app/assets/"').read()
deployed_map = sorted(x for x in out.split() if x.startswith("pages-map-index.") and x.endswith(".js"))
check(bool(local_map) and local_map[-1] in deployed_map,
      "线上 JS 与本地构建一致（本地 %s / 线上 %s）" % (local_map[-1:], deployed_map))
icon_out = os.popen('docker exec jingxuan-nginx-1 sh -c "ls /usr/share/nginx/html/jingxuan/app/static/icons/"').read()
check("plant.svg" in icon_out and "map.svg" in icon_out, "图标资源已部署到线上")

print("== 4. 图标与功能 ==")
tok = api("/api/auth/login", method="POST", body={"username": "stu1", "password": "admin123", "rememberMe": False})["data"]["token"]

# 我的植物：封面字段
obs = api("/api/student/plant/observations?page=1&size=20", token=tok)["data"]
records = obs.get("records") or []
with_cover = [r for r in records if r.get("coverUrl")]
with_count = [r for r in records if r.get("photoCount") is not None]
check(len(with_cover) > 0, "我的植物接口返回 coverUrl（%d/%d 条）" % (len(with_cover), len(records)))
check(len(with_count) == len(records) and records, "接口返回 photoCount（%d 条）" % len(with_count))

# 消息：标记已读
notes = api("/api/student/notify/list?page=1&size=20", token=tok)["data"].get("records") or []
unread_before = int(api("/api/student/notify/unread-count", token=tok)["data"]["count"])
if notes:
    api("/api/student/notify/read/%s" % notes[0]["id"], token=tok, method="POST", body={})
    unread_after = int(api("/api/student/notify/unread-count", token=tok)["data"]["count"])
    check(unread_after <= unread_before, "标记已读接口生效（%s → %s）" % (unread_before, unread_after))
    api("/api/student/notify/read-all", token=tok, method="POST", body={})
    check(int(api("/api/student/notify/unread-count", token=tok)["data"]["count"]) == 0, "全部已读接口生效")
else:
    check(False, "没有通知数据，无法验证已读（请先产生一条通知）")

print()
if failures:
    print("失败项 %d 个：" % len(failures))
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("收尾校验全部通过")
