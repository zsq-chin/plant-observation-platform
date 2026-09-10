#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为演示环境灌入真实植物照片（来源 Wikimedia Commons，保留署名与许可信息）。

流程（全部走后端正规接口，不直接改库）：
  1) Commons API 检索并下载 1280px JPEG（校验魔数与体积）
  2) 学生创建草稿 -> 上传照片（后端落盘 + 生成 jpg 缩略图 + 写 DB）
  3) 补全物种/省市区/观察时间/描述 -> 提交审核
  4) 教师批量审核通过 -> 进入展廊/首页/地图
  5) 校验 fileUrl 与 thumbnailUrl HTTP 200 且为图片，并输出署名清单

用法：
  python -X utf8 scripts/seed-demo-photos.py --base http://127.0.0.1:8080
  python -X utf8 scripts/seed-demo-photos.py --per-species 2 --include-local-webp
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

COMMONS_API = "https://commons.wikimedia.org/w/api.php"
UA = "jingxuan-plant-demo/1.0 (educational demo)"

# 物种 -> (Commons 检索词, 器官标签, 省/市/区县, 观察时间, 中文描述)
SPECIES_PLAN = [
    ("银杏", "Ginkgo biloba leaves autumn", "LEAF", "330000", "330100", "330106",
     "2026-04-06 09:30:00", "校园行道树，扇形叶片，秋季转金黄。"),
    ("珙桐", "Davidia involucrata flowers", "FLOWER", "510000", "510100", "510107",
     "2026-04-20 10:15:00", "珍稀孑遗植物，花期白色苞片似飞鸽。"),
    ("牡丹", "Paeonia suffruticosa flower", "FLOWER", "410000", "410300", "410302",
     "2026-04-12 08:50:00", "花坛栽培，花大色艳，重瓣。"),
    ("狗尾草", "Setaria viridis grass", "WHOLE", "110000", "110100", "110108",
     "2026-07-18 16:40:00", "操场边荒地成片生长，穗形似狗尾。"),
    ("爬山虎", "Parthenocissus tricuspidata autumn", "LEAF", "320000", "320100", "320102",
     "2026-09-08 15:20:00", "教学楼墙面攀援，秋叶转红。"),
]

FAILURES = []


def log(msg):
    print(msg, flush=True)


def fail(msg):
    FAILURES.append(msg)
    print("[FAIL] " + msg, flush=True)


def http_json(method, url, token=None, body=None, expect=200):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            return exc.code, json.loads(raw)
        except Exception:
            return exc.code, {"raw": raw}


def upload_photo(base, obs_id, token, file_path, organ):
    boundary = "----demo" + uuid.uuid4().hex
    with open(file_path, "rb") as fh:
        payload = fh.read()
    fname = os.path.basename(file_path)
    head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fname +
            "\"\r\nContent-Type: image/jpeg\r\n\r\n").encode("utf-8")
    organ_part = ("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"organType\"\r\n\r\n" +
                  organ + "\r\n--" + boundary + "--\r\n").encode("utf-8")
    body = head + payload + organ_part
    url = base + "/api/student/plant/observations/" + str(obs_id) + "/photos"
    req = urllib.request.Request(url, data=body, method="POST", headers={
        "Authorization": "Bearer " + token,
        "Content-Type": "multipart/form-data; boundary=" + boundary,
    })
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


def commons_search(query, limit=8):
    params = {
        "action": "query", "format": "json", "generator": "search",
        "gsrsearch": query, "gsrnamespace": "6", "gsrlimit": str(limit),
        "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": "1280",
    }
    url = COMMONS_API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=45) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    pages = (data.get("query") or {}).get("pages") or {}
    out = []
    for page in pages.values():
        info = (page.get("imageinfo") or [{}])[0]
        if info.get("mime") != "image/jpeg" or not info.get("thumburl"):
            continue
        if (info.get("width") or 0) < 800:
            continue
        meta = info.get("extmetadata") or {}
        out.append({
            "title": page.get("title"),
            "thumburl": info.get("thumburl"),
            "pageurl": info.get("descriptionurl"),
            "width": info.get("width"), "height": info.get("height"),
            "artist": strip_html((meta.get("Artist") or {}).get("value") or "未标注"),
            "license": (meta.get("LicenseShortName") or {}).get("value") or "未标注",
            "credit": strip_html((meta.get("Credit") or {}).get("value") or ""),
        })
    return out


def strip_html(text):
    plain = re.sub(r"<[^>]+>", "", text or "")
    return re.sub(r"\s+", " ", plain).strip()[:120]


def download(url, target, min_bytes=20000):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as resp:
        blob = resp.read()
    if len(blob) < min_bytes:
        raise RuntimeError("下载体积过小: %d bytes" % len(blob))
    if blob[:2] != b"\xff\xd8":
        raise RuntimeError("不是 JPEG 数据")
    with open(target, "wb") as fh:
        fh.write(blob)
    return len(blob), hashlib.sha256(blob).hexdigest()[:16]


def probe_image(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            ctype = resp.headers.get("Content-Type") or ""
            length = int(resp.headers.get("Content-Length") or 0)
            return resp.status, ctype, length
    except Exception as exc:
        return -1, str(exc), 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--student", default="stu1")
    parser.add_argument("--teacher", default="tea1")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--per-species", type=int, default=1,
                        help="每个物种入库的图片张数上限（默认 1；重复运行会复用已有真实照片记录，不会重复建单）")
    parser.add_argument("--credits", default="docs/demo-photo-credits.md")
    parser.add_argument("--tmp", default="data/plant-media/_incoming")
    parser.add_argument("--include-local-webp", action="store_true",
                        help="把 data/plant-media 中未入库的 OIP-C.webp 作为待鉴定观察重新入库")
    parser.add_argument("--no-approve", action="store_true", help="只提交不审核")
    args = parser.parse_args()

    base = args.base.rstrip("/")
    os.makedirs(args.tmp, exist_ok=True)

    status, payload = http_json("POST", base + "/api/auth/login",
                                body={"username": args.student, "password": args.password})
    if payload.get("code") != 200:
        print("学生登录失败: " + json.dumps(payload, ensure_ascii=False))
        return 2
    stu = payload["data"]["token"]
    status, payload = http_json("POST", base + "/api/auth/login",
                                body={"username": args.teacher, "password": args.password})
    tea = payload["data"]["token"] if payload.get("code") == 200 else None

    # 已有真实大图（>=20KB 封面）的待审记录：直接复用并纳入审核，避免重复建单
    reused = {}
    if tea:
        for state in ("SUBMITTED", "APPROVED"):
            st_r, rev = http_json("GET", base + "/api/teacher/plant/reviews?status=" + state + "&size=100", tea)
            for item in ((rev.get("data") or {}).get("records") or []):
                name = item.get("commonName") or item.get("reportedCommonName")
                cover = item.get("coverUrl")
                if not name or not cover:
                    continue
                _, ctype, length = probe_image(base + cover)
                if length > 20000:
                    reused.setdefault(name, []).append((str(item.get("observationId")), state))

    existing_credits = load_existing_credits(args.credits)
    credits = []
    created = []
    for common, query, organ, prov, city, district, observed_at, desc in SPECIES_PLAN:
        if reused.get(common):
            obs_reuse, reuse_state = reused[common][0]
            status, ph = http_json("GET", base + "/api/student/plant/observations/" + obs_reuse + "/photos", stu)
            photo = (ph.get("data") or [{}])[0]
            _, _, real_len = probe_image(base + (photo.get("fileUrl") or ""))
            if reuse_state == "SUBMITTED":
                created.append(obs_reuse)  # 仅待审记录才需要再走审核
            credits.append({
                "common": common, "scientific": None, "observationId": obs_reuse,
                "fileUrl": photo.get("fileUrl"), "thumbnailUrl": photo.get("thumbnailUrl"),
                "localBytes": int(real_len or 0), "sha256": "-",
                "commonsTitle": (existing_credits.get(common) or {}).get("title") or "（复用此前入库的 Commons 素材）",
                "commonsPage": (existing_credits.get(common) or {}).get("page") or "",
                "artist": (existing_credits.get(common) or {}).get("artist") or "见 docs/demo-photo-credits.md",
                "license": (existing_credits.get(common) or {}).get("license") or "见该文件",
                "size": (existing_credits.get(common) or {}).get("size") or "existing",
            })
            log("[REUSE] %s -> 观察 %s（状态 %s，已有真实照片）" % (common, obs_reuse, reuse_state))
            continue
        kw = urllib.parse.quote(common)
        status, sp = http_json("GET", base + "/api/public/plant/species/search?keyword=" + kw)
        records = (sp.get("data") or {}).get("records") or []
        hit = next((r for r in records if r.get("commonName") == common), None)
        if hit is None:
            fail("物种未找到: " + common)
            continue
        try:
            candidates = commons_search(query)
        except Exception as exc:
            fail("Commons 检索失败 %s: %s" % (common, exc))
            continue
        picked = None
        for cand in candidates:
            try:
                size, digest = download(cand["thumburl"], os.path.join(args.tmp, common + ".jpg"))
                cand["bytes"] = size
                cand["sha256"] = digest
                picked = cand
                break
            except Exception as exc:
                log("  跳过 %s（%s）" % (cand.get("title"), exc))
        if picked is None:
            fail("没有可用图片: " + common)
            continue

        status, draft = http_json("POST", base + "/api/student/plant/observations", stu,
                                  {"provinceCode": prov, "cityCode": city, "districtCode": district,
                                   "locationText": "校园植物园"})
        obs_id = (draft.get("data") or {}).get("id")
        if not obs_id:
            fail("创建草稿失败 %s: %s" % (common, json.dumps(draft, ensure_ascii=False)))
            continue
        local_file = os.path.join(args.tmp, common + ".jpg")
        try:
            status, up = upload_photo(base, obs_id, stu, local_file, organ)
        except Exception as exc:
            fail("上传失败 %s: %s" % (common, exc))
            continue
        photo = (up.get("data") or {})
        if up.get("code") != 200 or not photo.get("fileUrl"):
            fail("上传返回异常 %s: %s" % (common, json.dumps(up, ensure_ascii=False)))
            continue
        status, upd = http_json("PUT", base + "/api/student/plant/observations/" + str(obs_id), stu,
                                {"speciesId": hit["id"], "reportedCommonName": common,
                                 "provinceCode": prov, "cityCode": city, "districtCode": district,
                                 "locationText": "校园植物园", "observedAt": observed_at,
                                 "description": desc})
        status, sub = http_json("POST", base + "/api/student/plant/observations/" + str(obs_id) + "/submit", stu, {})
        if sub.get("code") != 200:
            fail("提交失败 %s: %s" % (common, json.dumps(sub, ensure_ascii=False)))
            continue
        created.append(str(obs_id))
        credits.append({
            "common": common, "scientific": hit.get("scientificName"), "observationId": str(obs_id),
            "fileUrl": photo.get("fileUrl"), "thumbnailUrl": photo.get("thumbnailUrl"),
            "localBytes": picked["bytes"], "sha256": picked["sha256"],
            "commonsTitle": picked["title"], "commonsPage": picked["pageurl"],
            "artist": picked["artist"], "license": picked["license"],
            "size": "%dx%d" % (picked["width"], picked["height"]),
        })
        log("[OK] %s -> 观察 %s  <%s>  %d bytes" % (common, obs_id, photo.get("fileUrl"), picked["bytes"]))

    # 用户手动放入、未被数据库引用的真实图片：作为待鉴定观察重新入库
    orphan = "data/plant-media/thumbnail/2097696993530740737/OIP-C.webp"
    # 入库成功后脚本会删除该游离文件，因此重复运行天然幂等
    if args.include_local_webp and os.path.exists(orphan):
        import shutil
        tmp_copy = os.path.join(args.tmp, "OIP-C.webp")
        shutil.copyfile(orphan, tmp_copy)
        status, draft = http_json("POST", base + "/api/student/plant/observations", stu,
                                  {"provinceCode": "330000", "cityCode": "330100"})
        obs_id = (draft.get("data") or {}).get("id")
        if obs_id:
            boundary = "----demo" + uuid.uuid4().hex
            with open(tmp_copy, "rb") as fh:
                blob = fh.read()
            head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"OIP-C.webp\"\r\n"
                    "Content-Type: image/webp\r\n\r\n").encode()
            tail = ("\r\n--" + boundary + "--\r\n").encode()
            req = urllib.request.Request(base + "/api/student/plant/observations/" + str(obs_id) + "/photos",
                                         data=head + blob + tail, method="POST",
                                         headers={"Authorization": "Bearer " + stu,
                                                  "Content-Type": "multipart/form-data; boundary=" + boundary})
            with urllib.request.urlopen(req, timeout=60) as resp:
                up = json.loads(resp.read().decode("utf-8"))
            photo = up.get("data") or {}
            if up.get("code") != 200 or not photo.get("fileUrl"):
                fail("本地素材上传失败: " + json.dumps(up, ensure_ascii=False))
                photo = {}
            http_json("PUT", base + "/api/student/plant/observations/" + str(obs_id), stu,
                      {"provinceCode": "330000", "cityCode": "330100", "districtCode": "330106",
                       "locationText": "校园（待鉴定素材）", "observedAt": "2026-09-09 10:00:00",
                       "description": "本地已有素材重新入库，物种待鉴定。", "unknownPlant": True})
            _, sub = http_json("POST", base + "/api/student/plant/observations/" + str(obs_id) + "/submit", stu, {})
            if sub.get("code") == 200:
                created.append(str(obs_id))
                credits.append({
                    "common": "待鉴定（本地素材 OIP-C.webp）", "scientific": None, "observationId": str(obs_id),
                    "fileUrl": photo.get("fileUrl"), "thumbnailUrl": photo.get("thumbnailUrl"),
                    "localBytes": len(blob), "sha256": hashlib.sha256(blob).hexdigest()[:16],
                    "commonsTitle": "（本地文件，非 Commons）", "commonsPage": "",
                    "artist": "用户提供", "license": "待确认",
                    "size": "local",
                })
                log("[OK] 本地素材 OIP-C.webp -> 观察 %s" % obs_id)
                if photo.get("fileUrl"):
                    try:
                        os.remove(orphan)  # 已入库为 uuid 文件，移除未引用的游离文件
                        log("     已清理游离文件: " + orphan)
                    except OSError as exc:
                        log("     游离文件清理失败: %s" % exc)

    if tea and created and not args.no_approve:
        status, rev = http_json("POST", base + "/api/teacher/plant/reviews/batch", tea,
                                {"observationIds": [int(x) for x in created], "action": "APPROVED",
                                 "comment": "演示素材：来源与许可已登记，审核通过。"})
        if rev.get("code") == 200:
            log("[OK] 教师批量审核通过 %d 条" % len(created))
        else:
            fail("批量审核失败: " + json.dumps(rev, ensure_ascii=False))

    log("")
    log("=== 图片可访问性校验 ===")
    for row in credits:
        for key in ("fileUrl", "thumbnailUrl"):
            url = row.get(key)
            if not url:
                continue
            code, ctype, length = probe_image(base + url)
            threshold = 2048  # 测试图仅 70~633 字节；真实原图与 320px 缩略图均 >2KB
            ok = code == 200 and ctype.startswith("image/") and length > threshold
            mark = "[PASS]" if ok else "[FAIL]"
            if not ok:
                FAILURES.append("图片不可访问: " + url)
            log("%s %-14s %s -> %s %s %d bytes" % (mark, row["common"], key, code, ctype, length))

    write_credits(args.credits, credits, existing_credits)
    log("")
    log("署名清单已写入: " + args.credits)
    if FAILURES:
        log("失败项: %d" % len(FAILURES))
        for item in FAILURES:
            log("  - " + item)
        return 1
    log("全部成功：%d 条真实照片观察已公开可访问" % len(credits))
    return 0


def load_existing_credits(path):
    """读取既有署名清单，复用分支据此保留作者/许可，避免重复运行时覆盖。"""
    if not os.path.exists(path):
        return {}
    known = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if not line.startswith("|") or "---" in line:
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 8 or cells[0] in ("物种",):
                continue
            known[cells[0]] = {
                "size": cells[2], "artist": cells[4], "license": cells[5],
                "page": cells[6], "title": cells[6], "bytes": cells[3],
                "observationId": cells[1], "fileUrl": cells[7] if len(cells) > 7 else "-",
            }
    return known


def write_credits(path, credits, existing=None):
    existing = existing or {}
    seen = {row["common"] for row in credits}
    for name, row in existing.items():
        if name in seen:
            continue
        credits = credits + [{
            "common": name, "observationId": row.get("observationId", "-"),
            "size": row.get("size", "-"), "localBytes": 0, "sha256": "-",
            "commonsTitle": row.get("title", "-"), "commonsPage": row.get("page", "-"),
            "artist": row.get("artist", "-"), "license": row.get("license", "-"),
            "fileUrl": row.get("fileUrl", "-"), "thumbnailUrl": "-",
        }]
    lines = [
        "# 演示图片署名与许可（摄影作品来源 Wikimedia Commons）",
        "",
        "演示环境中的植物照片来自 Wikimedia Commons 自由许可作品，仅用于教学演示。",
        "如需对外正式发布，请保留下表作者与许可信息（CC BY / CC BY-SA 要求署名并注明许可），或替换为学校自摄照片。",
        "",
        "| 物种 | 观察ID | 图片尺寸 | 体积 | 作者 | 许可 | Commons 页面 | 本地 URL |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for row in credits:
        size_text = ("%d B" % row["localBytes"]) if row.get("localBytes") else "-"
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
            row["common"], row["observationId"], row["size"], size_text,
            row["artist"].replace("|", "/"), row["license"].replace("|", "/"),
            row["commonsPage"] or "-", row["fileUrl"]))
    lines.append("")
    lines.append("生成时间：" + time.strftime("%Y-%m-%d %H:%M:%S"))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    sys.exit(main())