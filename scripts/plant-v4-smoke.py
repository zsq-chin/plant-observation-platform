#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V4 冒烟：Actuator/防爆破/dashboard/搜索/建议/批量审核（运行栈 HTTP 实测）。"""
import base64
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get("PLANT_BASE", "http://127.0.0.1:8080")
FAILURES = 0


def check(cond, msg):
    global FAILURES
    print(("[PASS] " if cond else "[FAIL] ") + msg)
    if not cond:
        FAILURES += 1


def call(method, path, token=None, body=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        try:
            return exc.code, json.loads(exc.read().decode("utf-8", errors="replace"))
        except Exception:
            return exc.code, None


def get(path, token=None):
    return call("GET", path, token)


def post(path, body=None, token=None):
    return call("POST", path, token, body if body is not None else {})


def login(user, password):
    st, payload = post("/api/auth/login", {"username": user, "password": password})
    if payload is None or payload.get("code") != 200:
        raise RuntimeError("login failed " + user)
    return payload["data"]["token"]


def upload_png(obs_id, token):
    boundary = "----b" + uuid.uuid4().hex
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
    part = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\n").encode() + png + b"\r\n" + ("--" + boundary + "--\r\n").encode()
    req = urllib.request.Request(BASE + "/api/student/plant/observations/" + str(obs_id) + "/photos", data=part, headers={"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


# 1) Actuator 健康公开 / 敏感端点匿名 403
st, payload = get("/actuator/health")
check(st == 200 and payload and payload.get("status") == "UP", "actuator/health 公开返回 UP")
st, _ = get("/actuator/env")
check(st in (401, 403), "敏感 actuator 端点不对匿名开放(实际 %s)" % st)

# 2) 登录防爆破
stu = login("stu1", "admin123")
for i in range(5):
    post("/api/auth/login", {"username": "stu_lock_test", "password": "wrong-pass"})
st, payload = post("/api/auth/login", {"username": "stu_lock_test", "password": "wrong-pass"})
check(payload is not None and payload.get("code") != 200 and "次数过多" in (payload.get("message") or ""), "连续失败后提示锁定")
subprocess.run(["docker", "exec", "jingxuan-redis-1", "redis-cli", "DEL", "jingxuan:login:fail:stu_lock_test"], check=True, capture_output=True)

# 3) 学生工作台/搜索/建议
st, payload = get("/api/student/plant/dashboard", stu)
check(payload.get("code") == 200 and payload.get("data") is not None, "学生 dashboard 可用")
st, payload = get("/api/public/plant/search?keyword=" + urllib.parse.quote("银杏"))
check(payload.get("code") == 200 and len(payload.get("data", {}).get("species", [])) >= 1, "全局搜索命中银杏物种")
st, payload = get("/api/public/plant/species/search?keyword=" + urllib.parse.quote("银杏"))
ginkgo = next((r for r in payload.get("data", {}).get("records", []) if r.get("commonName") == "银杏"), None)
check(ginkgo is not None, "物种搜索返回银杏标准物种")
name = "建议植物" + str(int(time.time()))[-5:]
st, payload = post("/api/student/plant/species-suggestions", {"suggestedCommonName": name, "description": "V4 冒烟建议"}, stu)
check(payload.get("code") == 200 and payload.get("data", {}).get("status") == "PENDING", "学生提交物种建议")
suggestion_id = payload["data"]["id"]

# 4) 教师批量审核两条新提交
tea = login("tea1", "admin123")
ids = []
for i in range(2):
    st, d = post("/api/student/plant/observations", {"provinceCode": "330000", "cityCode": "330100", "reportedCommonName": "银杏"}, stu)
    check(d.get("code") == 200 and d.get("data", {}).get("id"), "学生创建观察草稿(%d/2)" % (i + 1))
    obs_id = d["data"]["id"]
    upload_png(obs_id, stu)
    st, u = call("PUT", "/api/student/plant/observations/" + str(obs_id), stu,
                 {"speciesId": ginkgo["id"], "provinceCode": "330000", "cityCode": "330100",
                  "districtCode": "330106", "locationText": "某某植物园",
                  "observedAt": "2026-09-08 10:00:00", "description": "V4 冒烟批量审核样本"})
    check(u.get("code") == 200, "草稿补充物种/观察时间(%d/2)" % (i + 1))
    st, sub = post("/api/student/plant/observations/" + str(obs_id) + "/submit", {}, stu)
    check(sub.get("code") == 200, "学生提交审核(%d/2)" % (i + 1))
    ids.append(obs_id)
st, payload = post("/api/teacher/plant/reviews/batch", {"observationIds": ids, "action": "APPROVED", "comment": "批量审核冒烟"}, tea)
check(payload.get("code") == 200, "教师批量审核通过")
st, payload = get("/api/public/plant/gallery?keyword=" + urllib.parse.quote("银杏") + "&sort=latest&size=20")
matched = [r for r in payload.get("data", {}).get("records", []) if str(r["observationId"]) in [str(i) for i in ids]]
check(len(matched) == 2, "批量通过后两条进入展廊")

# 5) 教师处理建议（自动建档）
st, payload = post("/api/teacher/plant/species-suggestions/" + str(suggestion_id) + "/decide", {"status": "APPROVED", "comment": "冒烟通过"}, tea)
check(payload.get("code") == 200 and payload.get("data", {}).get("status") == "APPROVED", "教师通过建议")
st, payload = get("/api/public/plant/species/search?keyword=" + urllib.parse.quote(name))
check(payload.get("code") == 200 and len(payload.get("data", {}).get("records", [])) >= 1, "建议物种自动建档可检索")

# 6) App 照片编辑能力：器官标签/排序/封面（V4 实测闭环）
st, d = post("/api/student/plant/observations", {"provinceCode": "330000"}, stu)
check(d.get("code") == 200 and d.get("data", {}).get("id"), "照片能力验证：创建草稿")
obs6 = d["data"]["id"]
up = upload_png(obs6, stu)
pid6 = str(up["data"]["id"])
st, o = call("PUT", "/api/student/plant/observations/" + str(obs6) + "/photos/" + pid6 + "/organ?organType=FLOWER", stu, {})
check(o.get("code") == 200, "修改照片器官标签成功")
st, ph = get("/api/student/plant/observations/" + str(obs6) + "/photos", stu)
check(ph.get("data", [{}])[0].get("organType") == "FLOWER", "器官标签已持久化")
st, order = call("PUT", "/api/student/plant/observations/" + str(obs6) + "/photos/order", stu, [int(pid6)])
check(order.get("code") == 200, "照片排序接口可用")
st, cover = call("PUT", "/api/student/plant/observations/" + str(obs6) + "/photos/cover?photoId=" + pid6, stu, {})
check(cover.get("code") == 200, "设为封面接口可用")
st, ph2 = get("/api/student/plant/observations/" + str(obs6) + "/photos", stu)
check(ph2.get("data", [{}])[0].get("isCover") == 1, "封面状态已生效")
call("DELETE", "/api/student/plant/observations/" + str(obs6), stu, {})

# 7) 清理：本次冒烟产生的 1x1 测试记录由管理员下线，避免污染展廊（V4 数据治理）
adm = login("admin", "admin123")
for oid in ids:
    st, off = post("/api/admin/plant/observations/" + str(oid) + "/offline", {}, adm)
    check(off.get("code") == 200, "冒烟测试记录已下线(%s)" % oid)
print("")
if FAILURES == 0:
    print("V4 冒烟全部通过")
    raise SystemExit(0)
print("V4 冒烟失败项: %d" % FAILURES)
raise SystemExit(1)
