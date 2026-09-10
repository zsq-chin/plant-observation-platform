#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全国植物观察与交流平台 E2E 主闭环冒烟（V1 验收场景 API 版）。
前置: docker compose up -d --build backend；账号 admin/admin123、tea1/admin123、stu1/stu2(admin123, 班级1班)。
用法: python -X utf8 scripts/plant-e2e-loop.py [http://127.0.0.1:8080]
"""
import os
import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
FAILURES = 0

def check(cond, msg):
    global FAILURES
    if cond:
        print(f"[PASS] {msg}")
    else:
        FAILURES += 1
        print(f"[FAIL] {msg}")

def call(method, path, token=None, body=None, form=None):
    url = BASE + path
    headers = {"Accept": "application/json"}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if form is not None:
        boundary = "----e2e" + uuid.uuid4().hex
        parts = []
        for key, value in form.items():
            if isinstance(value, tuple):  # file: (filename, bytes, mime)
                filename, content, mime = value
                parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"; filename=\"{filename}\"; type=\"{mime}\"\r\nContent-Type: {mime}\r\n\r\n".encode())
                parts.append(content)
                parts.append(b"\r\n")
            else:
                parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{key}\"\r\n\r\n".encode())
                parts.append(str(value).encode("utf-8"))
                parts.append(b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        data = b"".join(parts)
        headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8")
            return resp.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as e:
        payload = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(payload)
        except Exception:
            return e.code, payload

def get(path, token=None):
    status, payload = call("GET", path, token)
    return status, payload

def post(path, body=None, token=None):
    return call("POST", path, token=token, body=body if body is not None else {})

def put(path, body, token=None):
    return call("PUT", path, token=token, body=body)

def fetch_image_status(url):
    try:
        with urllib.request.urlopen(url, timeout=15) as resp:
            ctype = resp.headers.get("Content-Type") or ""
            return resp.status, ctype.startswith("image"), resp.length
    except Exception:
        return -1, False, 0

def abs_url(path):
    return path if path.startswith("http") else BASE + path

def login(username, password):
    status, payload = post("/api/auth/login", {"username": username, "password": password, "rememberMe": False})
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"login failed {username}: {payload}")
    return payload["data"]["token"]

# ---------- 1) 公开基础数据 ----------
_, provinces = get("/api/public/plant/regions/provinces")
check(len(provinces["data"]) >= 34, f"公开接口返回 34+ 省级行政区 (实际 {len(provinces['data'])})")
_, search = get("/api/public/plant/species/search?keyword=" + urllib.parse.quote("银杏"))
ginkgo = next((r for r in search["data"]["records"] if r["commonName"] == "银杏"), None)
check(ginkgo is not None, "物种搜索可命中银杏标准物种")

# ---------- 2) 学生：草稿 -> 传图 -> 补充 -> 提交 ----------
stu_token = login("stu1", "admin123")
status, draft = post("/api/student/plant/observations", {"provinceCode": "330000", "cityCode": "330100", "districtCode": "330106", "locationText": "某某植物园"}, stu_token)
check(draft is not None and draft.get("data", {}).get("id"), "学生创建观察草稿")
obs_id = draft["data"]["id"]
png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
status, upload = call("POST", f"/api/student/plant/observations/{obs_id}/photos", token=stu_token, form={"file": ("leaf.png", png, "image/png"), "organType": "LEAF"})
check(upload is not None and upload.get("data", {}).get("fileUrl", "").startswith("/media/plants/"), "上传植物照片成功(/media 存储约定)")
# 30) 上传后物理文件存在 + fileUrl/thumbnailUrl HTTP 200（V3 §19）
file_url = upload["data"]["fileUrl"]
thumb_url = upload["data"].get("thumbnailUrl") or file_url
status, is_image, size = fetch_image_status(abs_url(file_url))
check(status == 200 and is_image and size > 0, f"fileUrl HTTP 200 且 Content-Type 为图片 (实际 {status})")
status, _, size = fetch_image_status(abs_url(thumb_url))
check(status == 200 and size > 0, f"thumbnailUrl HTTP 200 (实际 {status})")
status, updated = put(f"/api/student/plant/observations/{obs_id}", {"speciesId": ginkgo["id"], "provinceCode": "330000", "cityCode": "330100", "districtCode": "330106", "locationText": "某某植物园", "observedAt": "2026-09-08 10:00:00", "description": "校园道路两侧的银杏，叶色金黄"}, stu_token)
check(updated["data"].get("reportedCommonName") == "银杏", "草稿补充物种信息成功")
status, submit = post(f"/api/student/plant/observations/{obs_id}/submit", {}, stu_token)
check(submit.get("code") == 200, "学生提交审核成功")

# ---------- 3) 越权防护 ----------
stu2_token = login("stu2", "admin123")
status, payload = post(f"/api/student/plant/observations/{obs_id}/submit", {}, stu2_token)
check(status != 200 or payload.get("code") != 200, "他人不能操作我的记录(业务拒绝)")
status, payload = get("/api/teacher/plant/reviews", stu2_token)
check(status == 403, f"学生访问教师审核接口被拒(403, 实际 {status})")

# ---------- 4) 教师审核 + 精选 ----------
tea_token = login("tea1", "admin123")
status, reviews = get("/api/teacher/plant/reviews?status=SUBMITTED", tea_token)
check(any(r["observationId"] == obs_id for r in reviews["data"]["records"]), "教师审核列表出现该记录")
status, approve = post(f"/api/teacher/plant/reviews/{obs_id}/approve", {"action": "APPROVED", "comment": "照片清晰，信息完整，审核通过"}, tea_token)
check(approve.get("code") == 200, "教师审核通过")
status, featured = post(f"/api/teacher/plant/observations/{obs_id}/featured?featured=true", {}, tea_token)
check(featured.get("code") == 200, "教师推荐为优秀观察")

# ---------- 5) 展廊 / 首页 / 地图 ----------
status, gallery = get("/api/public/plant/gallery?keyword=" + urllib.parse.quote("银杏"))
item = next((r for r in gallery["data"]["records"] if r["observationId"] == obs_id), None)
check(item is not None and item.get("featured") is True, "审核通过记录进入展廊且精选")
code, is_image, _ = fetch_image_status(abs_url(item.get("coverUrl") or ""))
check(item.get("coverUrl") and code == 200 and is_image, f"展廊卡片封面真实可访问 (code={code}, image={is_image})")
status, home = get("/api/public/plant/home")
check(int(home["data"]["statistics"]["observationCount"]) >= 1, "首页统计 observationCount>=1")
check(any(r["observationId"] == obs_id for r in home["data"]["featuredObservations"]), "首页精选出现该记录")
feat_cover = next((r.get("coverUrl") for r in home["data"]["featuredObservations"] if r["observationId"] == obs_id), None)
code, is_image, _ = fetch_image_status(abs_url(feat_cover or ""))
check(feat_cover and code == 200 and is_image, f"首页精选封面真实可访问 (code={code})")
status, detail = get(f"/api/public/plant/observations/{obs_id}")
check(len(detail["data"]["photos"]) >= 1, "公开详情含照片")
for photo in detail["data"]["photos"]:
    code, is_image, _ = fetch_image_status(abs_url(photo["fileUrl"]))
    check(code == 200 and is_image, f"详情图片可访问 (code={code})")
check(detail["data"]["reviewComment"] == "照片清晰，信息完整，审核通过", "详情展示教师审核意见")
status, mapdata = get("/api/public/plant/map/china")
zj = next((r for r in mapdata["data"] if r["provinceCode"] == "330000"), None)
check(zj is not None and int(zj["observationCount"]) >= 1, "中国地图浙江省统计 +1")
status, prov = get("/api/public/plant/map/provinces/330000/species")
check(any(r["speciesId"] == ginkgo["id"] for r in prov["data"]), "浙江植物目录含银杏")

# 35) 省内可视化聚合（V3 §35）含真实缩略图封面
status, viz = get("/api/public/plant/map/provinces/330000/visualization")
check(viz.get("code") == 200, "省份可视化接口可用")
viz_data = viz.get("data") or {}
region = next((r for r in viz_data.get("regions", []) if r.get("regionCode") == "330100"), None)
check(region is not None and region.get("centerLng") is not None, "可视化包含城市节点与中心坐标")
species_hit = next((s for s in viz_data.get("topSpecies", []) if s.get("speciesId") == ginkgo["id"]), None)
if species_hit and species_hit.get("coverUrl"):
    code, is_image, _ = fetch_image_status(abs_url(species_hit["coverUrl"]))
    check(code == 200 and is_image, "可视化 topSpecies 封面真实可访问")
else:
    check(False, "可视化 topSpecies 应包含该物种")
# ---------- 6) 师生互动：评论/回复/评分/置顶 ----------
status, comment = post(f"/api/community/plant/observations/{obs_id}/comments", {"content": "拍得真清楚，银杏叶很好看"}, stu2_token)
check(comment["data"].get("id") is not None, "stu2 发表评论")
status, reply = post(f"/api/community/plant/observations/{obs_id}/comments", {"content": "谢谢，欢迎也去观察", "parentId": comment["data"]["id"]}, stu_token)
check(reply["data"].get("parentId") == comment["data"]["id"], "作者回复评论")
status, tea_comment = post(f"/api/community/plant/observations/{obs_id}/comments", {"content": "建议补充叶缘特写，是很好的校园观察素材"}, tea_token)
status, pin = post(f"/api/community/plant/comments/{tea_comment['data']['id']}/pin?pinned=true", {}, tea_token)
check(pin.get("code") == 200, "教师点评置顶成功")
status, rate = post(f"/api/community/plant/observations/{obs_id}/rating", {"score": 5}, stu2_token)
check(rate.get("code") == 200, "学生 5 星评价成功")
status, rate2 = post(f"/api/community/plant/observations/{obs_id}/rating", {"score": 4}, tea_token)
check(rate2.get("code") == 200, "教师评分成功(一人一条)")
status, comments = get(f"/api/public/plant/observations/{obs_id}/comments")
check(any(c.get("isPinned") for c in comments["data"]), "公开评论列表教师置顶可见")
status, home2 = get("/api/public/plant/home")
check(len(home2["data"]["teacherComments"]) >= 1, "首页最新教师点评出现")
status, detail2 = get(f"/api/public/plant/observations/{obs_id}")
check(float(detail2["data"]["averageRating"]) >= 4.0 and int(detail2["data"]["ratingCount"]) >= 2, "详情平均分与评分人数正确")

# ---------- 7) 管理员下线/上架 ----------
admin_token = login("admin", "admin123")
status, offline = post(f"/api/admin/plant/observations/{obs_id}/offline", {}, admin_token)
check(offline.get("code") == 200, "管理员强制下线成功")
status, gallery2 = get("/api/public/plant/gallery?keyword=" + urllib.parse.quote("银杏"))
check(all(r["observationId"] != obs_id for r in gallery2["data"]["records"]), "下线后从展廊消失")
status, online = post(f"/api/admin/plant/observations/{obs_id}/online", {}, admin_token)
check(online.get("code") == 200, "管理员重新上架")


# 39) 容器重启后图片仍可访问（PLANT_E2E_RESTART=1 时执行，V3 §20/73）
if os.environ.get("PLANT_E2E_RESTART") == "1":
    import subprocess
    import time
    probe_urls = []
    payload = detail2.get("data") or {} if "detail2" in dir() else {}
    if isinstance(payload, dict) and payload.get("photos"):
        probe_urls = [p["fileUrl"] for p in payload["photos"]]
    subprocess.run(["docker", "restart", "jingxuan-backend-1"], check=True, capture_output=True)
    ready = False
    for _ in range(30):
        time.sleep(5)
        try:
            st, body = call("POST", "/api/auth/login", body={"username": "admin", "password": "admin123", "rememberMe": False})
            if st == 200 and body.get("code") == 200:
                ready = True
                break
        except Exception:
            pass
    check(ready, "backend 容器重启完成")
    for url in probe_urls:
        code, is_image, size = fetch_image_status(abs_url(url))
        check(code == 200 and is_image and size > 0, "重启后图片仍可访问 (code=%d)" % code)

print("")
if FAILURES == 0:
    print("E2E 主闭环全部通过")
    sys.exit(0)
else:
    print("E2E 失败项: %d" % FAILURES)
    sys.exit(1)