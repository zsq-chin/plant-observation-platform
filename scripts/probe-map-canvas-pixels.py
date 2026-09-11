# -*- coding: utf-8 -*-
"""直接读取地图 canvas 像素：判断"地图到底画出来没有"，而不是只看元素尺寸。"""
import json
from playwright.sync_api import sync_playwright

APP = "http://10.120.46.175/jingxuan/app/"

PROBE = """() => {
  const c = document.querySelector('.china-map canvas');
  if (!c) return { err: 'no canvas element' };
  const box = c.getBoundingClientRect();
  let data = null;
  try {
    const ctx = c.getContext('2d');
    data = ctx.getImageData(0, 0, c.width, c.height).data;
  } catch (e) { return { err: 'getImageData failed: ' + e.message, w: c.width, h: c.height }; }
  let opaque = 0, nonBg = 0, samples = 0;
  const hist = {};
  const bg = [247, 250, 244];
  for (let i = 0; i < data.length; i += 4 * 53) {
    samples++;
    const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
    if (a > 10) opaque++;
    if (a > 10 && (Math.abs(r - bg[0]) + Math.abs(g - bg[1]) + Math.abs(b - bg[2]) > 24)) nonBg++;
    const key = [(r >> 5) << 5, (g >> 5) << 5, (b >> 5) << 5, a > 10 ? 'opaque' : 'clear'].join(',');
    hist[key] = (hist[key] || 0) + 1;
  }
  const top = Object.entries(hist).sort((x, y) => y[1] - x[1]).slice(0, 6);
  return {
    cssW: Math.round(box.width), cssH: Math.round(box.height),
    bufW: c.width, bufH: c.height,
    samples, opaque, nonBg,
    nonBgRatio: +(nonBg / Math.max(1, samples)).toFixed(3),
    topColors: top,
  };
}"""

with sync_playwright() as p:
    b = p.chromium.launch()
    for label, vp, dpr in (("桌面 390x844@2", {"width": 390, "height": 844}, 2),
                           ("手机 360x780@3", {"width": 360, "height": 780}, 3),
                           ("手机 412x915@2.6", {"width": 412, "height": 915}, 2.6)):
        ctx = b.new_context(viewport=vp, device_scale_factor=dpr, is_mobile=True, has_touch=True)
        page = ctx.new_page()
        logs = []
        page.on("console", lambda m: logs.append(m.type + ": " + m.text[:160]))
        page.goto(APP + "#/pages/map/index", wait_until="networkidle")
        page.wait_for_timeout(5000)
        out = page.evaluate(PROBE)
        print("---", label)
        print("  canvas:", json.dumps({k: v for k, v in out.items() if k != "topColors"}, ensure_ascii=False))
        print("  主色:", json.dumps(out.get("topColors"), ensure_ascii=False))
        print("  日志:", [l for l in logs if l.startswith(("log:", "error:", "warning:"))][:6])
        ctx.close()
    b.close()
