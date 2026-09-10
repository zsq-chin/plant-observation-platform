# 植物平台运维说明（V3 §70/§80/§83/§84）

## 数据与目录
- 植物图片持久化目录：`./data/plant-media/`（original/{observationId}/*、thumbnail/{observationId}/*），由 backend 与 nginx 容器共享（docker-compose volumes）。
- 图片 URL 约定：`/media/plants/original|thumbnail/...`；nginx 生产直接 alias 服务（30 天缓存），后端 8080 亦提供。
- 行政区展示数据：`sys_region`（34 省/363 市/2840 区县 + center/min/max 可视化字段）；来源与合规见 `docs/plant-map-data-source-and-compliance.md`。

## 健康与一致性（管理端）
```bash
curl -H "Authorization: Bearer <admin-token>" http://127.0.0.1:8080/api/admin/plant-media/health
curl -H "Authorization: Bearer <admin-token>" http://127.0.0.1:8080/api/admin/plant-media/consistency
```
只报告，不自动删除。

## 备份与恢复
必须同时备份：MySQL + ./data/plant-media。
```bash
# 数据库
docker exec jingxuan-mysql-1 sh -c "MYSQL_PWD=$DB_PASSWORD mysqldump -ujingxuan jingxuan" > backup/database/jingxuan.sql
# 图片目录（冷备或在线 tar）
tar -C data -czf backup/plant-media/plant-media-$(date +%F).tar.gz plant-media
```
恢复后应至少执行一次 GET 抽查与 /consistency 扫描。

## 旧图迁移
```bash
python -X utf8 scripts/migrate-plant-images.py   # /uploads/plant/** -> data/plant-media 并更新 DB
```

## 行政区数据更新/重导
```bash
python -X utf8 scripts/import-plant-regions.py --districts --apply
```

## E2E 与回归
```bash
python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080        # 常规闭环
PLANT_E2E_RESTART=1 python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080   # 含 backend 重启持久化
```
CI：`.github/workflows/ci.yml` 的 `plant-docker-e2e` 任务在 Linux 构建整套栈并执行上述闭环；`backend-quality` 在 Linux Testcontainers 执行单元+集成测试（含 PlantPlatformApiTest）。

## 图片删除/下线语义
- DRAFT/REJECTED：学生可删除照片（DB+物理文件）；删除草稿级联清理；
- APPROVED：不经撤回/重审不改图；管理员下线仅移出公开数据，不删物理文件。

## 地图说明
- 3D 页面：/jingxuan/plant/map（MapLibre GL；省级 fill-extrusion，数据高度=公开观察数）；WebGL 不可用时自动降级二维列表。
- 边界 GeoJSON：frontend/public/geo/china-provinces.json（DataV 来源，见合规文档）；省界/审图号正式公开展示前按合规文档更换。

## 演示数据准备（真实植物照片）
- `scripts/seed-demo-photos.py`：从 Wikimedia Commons 检索并下载 1280px JPEG（自由许可），走正规接口建草稿 → 上传（后端落盘/缩略图/写库）→ 补全物种与省市区 → 提交 → 教师批量审核通过，使展廊/首页/地图/App 显示真实照片；重复运行复用已有记录（幂等），并会把本地未入库素材重新入库。
  `python -X utf8 scripts/seed-demo-photos.py --base http://127.0.0.1:8080 --include-local-webp`
- 署名与许可：`docs/demo-photo-credits.md`（作者 / 许可 / Commons 页面 / 本地 URL）。CC BY / CC BY-SA 作品对外展示需保留署名；脚本重跑会保留历史行。

## 测试数据治理
- `scripts/cleanup-demo-test-photos.py`：把冒烟/E2E 产生的 1×1 测试图（70B/633B 照片）观察记录经管理员「强制下线」移出展廊/首页/地图（保留数据，不物理删除），并复核展廊封面体积。
  `python -X utf8 scripts/cleanup-demo-test-photos.py --base http://127.0.0.1:8080`
- `scripts/plant-v4-smoke.py` 结束时会自动下线本次冒烟创建的测试记录，避免污染公开数据。

## 图片上传格式与显示（排障手册）
- 支持格式：jpg / jpeg / png / webp / gif（植物域白名单 + 魔数校验 + 图片炸弹防护）；单张 ≤10MB、边长 ≤10000px、像素 ≤5000 万。
- webp 不生成缩略图，`thumbnailUrl` 回退为原图 URL（前端 <img> 直接显示，主流浏览器均支持）。
- 图片 URL 约定 `/media/plants/**`：容器部署由 nginx 只读卷提供（frontend/nginx.conf），裸机部署需保留 `location /media/plants/` 规则。
- 前端图片空白排查顺序：① `curl -I http://<host>/media/plants/...` 是否 200 且 Content-Type 为图片；② 浏览器硬刷新（Ctrl+F5）避开缓存；③ 查该记录照片文件是否真实存在（历史数据可能引用了已缺失文件，用 consistency 接口或 cleanup 脚本治理）。
