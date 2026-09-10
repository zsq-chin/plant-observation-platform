# -*- coding: utf-8 -*-
import json
from playwright.sync_api import sync_playwright

APP = "http://10.120.46.175/jingxuan/app/"
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    page = ctx.new_page()
    page.goto(APP, wait_until="networkidle")
    page.wait_for_timeout(1500)
    inputs = page.locator("uni-input input")
    inputs.nth(0).fill("stu1")
    inputs.nth(1).fill("admin123")
    page.locator("uni-button").first.click()
    page.wait_for_timeout(2500)
    page.goto(APP + "#/pages/gallery/index")
    page.wait_for_timeout(3000)

    out = page.evaluate("""() => {
      const res = {};
      res.location = location.href;
      res.origin = location.origin;
      res.baseURI = document.baseURI;
      const t = document.createElement('img');
      t.src = '/media/plants/original/x/y.png';
      res.plainImgResolved = t.src;
      const u = document.querySelector('uni-image');
      res.uniImageOuter = u ? u.outerHTML.slice(0, 400) : null;
      const inner = document.querySelector('uni-image img');
      res.innerImg = inner ? { src: inner.src, attr: inner.getAttribute('src'), nw: inner.naturalWidth } : null;
      res.baseTags = Array.from(document.querySelectorAll('base')).map(b => b.outerHTML);
      return res;
    }""")
    print(json.dumps(out, ensure_ascii=False, indent=2))
    ctx.close(); b.close()
