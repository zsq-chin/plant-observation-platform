#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查图片访问链路：登录 → 建草稿 → 上传 → GET fileUrl/thumbnailUrl → 断言 200 且为图片。

用法： python -X utf8 scripts/check-photo-access.py --base http://127.0.0.1:8080
"""
import argparse
import base64
import json
import sys
import urllib.error
import urllib.request
import uuid

FAIL = []


def check(cond, msg):
    print(("[PASS] " if cond else "[FAIL] ") + msg)
    if not cond:
        FAIL.append(msg)


def call(base, method, path, token=None, body=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return json.loads(exc.read().decode("utf-8", errors="replace"))


def upload(base, obs_id, token):
    boundary = "----chk" + uuid.uuid4().hex
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
    body = (
        ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"check.png\"\r\n")
        + "Content-Type: image/png\r\n\r\n"
    ).encode() + png + ("\r\n--" + boundary + "--\r\n").encode()
    req = urllib.request.Request(
        base + "/api/student/plant/observations/" + str(obs_id) + "/photos",
        data=body,
        method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def probe(url):
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            return resp.status, resp.headers.get("Content-Type") or "", int(resp.headers.get("Content-Length") or 0)
    except urllib.error.HTTPError as exc:
        return exc.code, "", 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--user", default="stu1")
    parser.add_argument("--password", default="admin123")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    login = call(base, "POST", "/api/auth/login", body={"username": args.user, "password": args.password})
    check(login.get("code") == 200, "学生登录成功")
    if login.get("code") != 200:
        return 1
    token = login["data"]["token"]

    draft = call(base, "POST", "/api/student/plant/observations", token, {"provinceCode": "330000", "cityCode": "330100"})
    obs_id = (draft.get("data") or {}).get("id")
    check(bool(obs_id), "创建观察草稿")
    if not obs_id:
        return 1

    up = upload(base, obs_id, token)
    photo = up.get("data") or {}
    check(up.get("code") == 200, "上传图片接口返回成功")
    check(str(photo.get("fileUrl", "")).startswith("/media/plants/"), "fileUrl 使用 /media/plants 约定")
    check(str(photo.get("thumbnailUrl", "")).startswith("/media/plants/"), "thumbnailUrl 使用 /media/plants 约定")

    for key in ("fileUrl", "thumbnailUrl"):
        url = photo.get(key)
        if not url:
            check(False, "%s 存在" % key)
            continue
        status, ctype, size = probe(base + url)
        check(status == 200, "%s 可直接访问（HTTP %s）" % (key, status))
        check(ctype.startswith("image/"), "%s 返回图片类型（%s）" % (key, ctype))
        check(size > 0, "%s 内容非空（%d 字节）" % (key, size))

    # 清理：删除测试草稿
    call(base, "DELETE", "/api/student/plant/observations/" + str(obs_id), token)
    print("")
    if FAIL:
        print("检查失败项：%d" % len(FAIL))
        for item in FAIL:
            print("  -", item)
        return 1
    print("图片访问链路检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
