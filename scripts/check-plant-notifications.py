# -*- coding: utf-8 -*-
"""校验：植物模块通知闭环（审核驳回/通过、教师点评、标记已读）。

注意：每次调用都校验业务 code，避免"HTTP 200 但业务失败"被当成成功（曾因此误判）。
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

API = "http://10.120.46.175"
failures = []


def check(ok, message):
    print(("  [PASS] " if ok else "  [FAIL] ") + message)
    if not ok:
        failures.append(message)


def raw(path, token=None, method="GET", body=None):
    req = urllib.request.Request(API + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(req, data, timeout=30) as r:
        return json.loads(r.read().decode())


def call(path, token=None, method="GET", body=None):
    """业务成功返回 data，业务失败直接抛错（防止静默失败）。"""
    result = raw(path, token, method, body)
    if result.get("code") not in (0, 200):
        raise RuntimeError("%s %s -> %s" % (method, path, result.get("message")))
    return result.get("data")


def login(username):
    return call("/api/auth/login", method="POST",
                body={"username": username, "password": "admin123", "rememberMe": False})["token"]


def upload(token, obs_id, filename, payload):
    boundary = "----jx" + uuid.uuid4().hex
    parts = [
        ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
         + "\"\r\nContent-Type: image/png\r\n\r\n").encode(),
        payload,
        ("\r\n--" + boundary + "--\r\n").encode(),
    ]
    req = urllib.request.Request(API + "/api/student/plant/observations/%s/photos?organType=WHOLE" % obs_id,
                                 data=b"".join(parts), method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def notifications(token, size=50):
    return call("/api/student/notify/list?page=1&size=%d" % size, token=token).get("records") or []


def unread(token):
    # 后端 Long 经 Jackson 统一序列化为字符串，这里显式转数值
    return int(call("/api/student/notify/unread-count", token=token)["count"])


PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c6300010000050001"
    "0d0a2db40000000049454e44ae426082")

DRAFT_BODY = {
    "provinceCode": "510000", "cityCode": "510100", "locationText": "通知校验",
    "reportedCommonName": "通知校验植物", "observedAt": "2026-09-10 09:00:00",
}

print("== 植物模块通知闭环 ==")
stu = login("stu1")
tea = login("tea1")

created = call("/api/student/plant/observations", token=stu, method="POST", body=DRAFT_BODY)
obs_id = str(created["id"])
upload(stu, obs_id, "notify.png", PNG)
call("/api/student/plant/observations/%s/submit" % obs_id, token=stu, method="POST", body={})
print("  已创建并提交观察", obs_id)

# --- 驳回 ---
call("/api/teacher/plant/reviews/%s/reject" % obs_id, token=tea, method="POST",
     body={"action": "REJECTED", "comment": "照片不够清晰，请重新拍摄"})
notes = notifications(stu)
rejected = [n for n in notes if "驳回" in (n.get("title") or "") and str(n.get("refId")) == obs_id]
check(bool(rejected), "驳回后学生收到通知")
if rejected:
    print("   ", rejected[0]["title"], "|", (rejected[0].get("content") or "")[:60])

# --- 标记已读 ---
if rejected:
    before = unread(stu)
    call("/api/student/notify/read/%s" % rejected[0]["id"], token=stu, method="POST", body={})
    after = unread(stu)
    check(after == before - 1, "标记已读后未读数 -1（%s → %s）" % (before, after))

# --- 重新提交并通过 ---
call("/api/student/plant/observations/%s" % obs_id, token=stu, method="PUT", body=DRAFT_BODY)
call("/api/student/plant/observations/%s/submit" % obs_id, token=stu, method="POST", body={})
call("/api/teacher/plant/reviews/%s/approve" % obs_id, token=tea, method="POST",
     body={"action": "APPROVED", "comment": "通过"})
notes = notifications(stu)
approved = [n for n in notes if "通过审核" in (n.get("title") or "") and str(n.get("refId")) == obs_id]
check(bool(approved), "通过审核后学生收到通知")

# --- 教师点评 ---
call("/api/community/plant/observations/%s/comments" % obs_id, token=tea, method="POST",
     body={"content": "记录很完整，继续保持！"})
notes = notifications(stu)
commented = [n for n in notes if "教师点评" in (n.get("title") or "") and str(n.get("refId")) == obs_id]
check(bool(commented), "教师点评后学生收到通知")

# --- 精选 ---
call("/api/teacher/plant/observations/%s/featured?featured=true" % obs_id, token=tea, method="POST", body={})
notes = notifications(stu)
featured = [n for n in notes if "优秀" in (n.get("title") or "") and str(n.get("refId")) == obs_id]
check(bool(featured), "推荐优秀后学生收到通知")

# --- 全部已读 ---
call("/api/student/notify/read-all", token=stu, method="POST", body={})
check(unread(stu) == 0, "全部已读后未读数为 0")

print("  保留校验记录供学生端页面展示:", obs_id)
print()
if failures:
    print("失败项 %d 个：" % len(failures))
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("通知闭环校验全部通过")
