# 全国植物观察与交流平台 — V4 学生 App 与正式上线 发布说明（v1.0-RC1）

> 依据《V4_学生App与正式上线》在 V2/V3（作品平台 → 全国植物观察平台，本地图片存储 + 3D 地图）基础上增量实现。
> 分支：`release/plant-platform-1.0-rc1`；前置文档：docs/plant-platform-transformation.md（V2）、docs/plant-platform-operations.md（V3）、docs/plant-map-data-source-and-compliance.md（V3 地图合规）、docs/student-app-android-build.md（App 打包）。

## 1. 交付总览（目标 → 实现 → 验证）

| V4 目标 | 实现位置 | 运行栈 HTTP 实测 |
|---|---|---|
| RC 基线分支 + 冻结/回归清单 | 本文档 §2/§3 | E2E 闭环 37 项 + V4 冒烟 23 项全绿 |
| 学生 Android App（uni-app） | student-app/（11 页：登录/首页/展廊/采集/编辑/我的植物/详情/地图/消息/我的/物种建议）；编辑页支持照片管理（点按：设封面/排序上移下移/器官标签/删除，后端 PUT photos/{photoId}/organ + order + cover） | `npm run build:h5` 通过；照片编辑闭环入冒烟实测 |
| 学生工作台 dashboard | GET /api/student/plant/dashboard（今日提交/待审/通过/被驳回/优秀/我的建议等统计） | V4 冒烟 PASS |
| 教师工作台统计 | GET /api/teacher/plant/dashboard；Web 端在「植物观察审核」页（PlantReviews.vue）顶部展示待审/提交/通过等统计条（审核工作台内嵌，非独立页面） | 冒烟/单测覆盖 |
| 批量审核 | POST /api/teacher/plant/reviews/batch（多选同意见 APPROVED/REJECTED）+ 教师审核页多选 UI | 冒烟 PASS（两条批量通过并进展廊） |
| 全局搜索 | GET /api/public/plant/search?keyword=（物种+观察聚合） | 冒烟 PASS（命中银杏） |
| 物种建议（V12 表） | plant_species_suggestion：学生提交 GET/POST /api/student/plant/species-suggestions；教师 GET /decide | 冒烟 PASS（建议→通过→自动建档可检索） |
| 未知植物 identification_status | plant_observation.identification_status（IDENTIFIED/PENDING），PENDING 允许无名称提交 | 冒烟/单测覆盖 |
| qualityWarnings/重复提示 | 审核详情 qualityWarnings（缺照片/缺省份/未选物种未标待鉴定/时间晚于当前/同人同种同省重复）；自动建档去重 | 单测 + 审核 UI 展示 |
| 图片炸弹防护 | LocalPlantPhotoStorageService：尺寸上限（长边）+ 像素上限读图校验 | PlantMediaStorageTest 单测 |
| 登录防爆破 | LoginThrottleService（Redis `jingxuan:login:fail:{user}`，5 次锁 10 分钟） | 冒烟 PASS（锁定提示） |
| 评论防刷 | PlantCommunityService（Redis `jingxuan:plant:comment:{userId}:{minute}`，>20/分钟拒绝） | PlantCommunityServiceTest 单测 |
| Actuator | health(公开,show-details=never)/info/metrics/prometheus；敏感端点 Security 保护（匿名 401） | 冒烟 PASS |
| 优雅停机 | server.shutdown: graceful + lifecycle timeout 30s | docker compose restart 实测 |
| 连接池参数 | 生产 profile HikariCP（maximum-pool-size 20 等，见 §6 修复记录） | /actuator/metrics/hikaricp.connections.max 实测 |
| Redis 键规范 | 全部业务键带 `jingxuan:` 前缀，见 docs/plant-platform-v4-production.md §3 | 键清单核查 |
| CI 门禁 | .github/workflows/ci.yml：backend-quality（单测+Testcontainers 集成）、plant-docker-e2e（全栈 E2E+冒烟）、前端质量 | push 后由 GitHub 执行（本地等价命令全绿） |
| 生产部署/备份恢复/监控告警/安全核对/手册/验收/试点 | docs/plant-platform-v4-production.md + 本仓库 docs 集 | 见各文档 |

## 2. RC 冻结清单（v1.0-RC1）

- [x] 分支 `release/plant-platform-1.0-rc1` 承载 V4 全部变更（未 squash 历史，可逐 commit 回滚）。
- [x] Flyway 只增不改：V1–V12 迁移按序应用（本地栈 `flyway_schema_history` 校验通过，重启不重放、不报错）。V12 仅加列/加表，不触碰既有行。
- [x] 演示/验收账号：admin、tea1（教师）、stu1、stu2（学生），密码 admin123（教师/管理员注册待审核；admin 已激活）。
- [x] 数据库种子：班级 1班/2班/3班、34 省级行政区 + 363 市 + 2840 区县、4 类别、5 标准物种（含银杏），V4 冒烟建议的物种已自动建档。
- [x] 图片存储本地化：`./data/plant-media`（original/thumbnail），/media/plants/** 由 nginx alias + 后端静态映射双路可访问（实测 HTTP 200，Content-Type image/jpeg）。
- [x] 前端构建产物含 geo 边界（nginx 容器 `/usr/share/nginx/html/jingxuan/geo/china-provinces.json` 实测 200）。
- [x] 安全默认值：Actuator 敏感端点不匿名开放；DeepSeek 审核失败 prod 拒绝；防爆破/评论限频/上传防炸弹均在默认配置生效。
- [ ] 生产域名/HTTPS 证书（部署时配置，见 production 文档 §1）；本地 HTTP 演示可接受。
- [ ] 生产环境密钥轮换（JWT_SECRET/DB_PASSWORD/MAIL_* 由 .env 注入，**不得入库**）。

## 3. 回归清单（每次发版前执行）

1. 后端单元测试：`mvn test`（本地 docker maven 镜像，422/422 通过，含 PlantPhotoServiceTest 封面/排序/器官 4 例）。
2. 前端：`npm run typecheck`、`npm run lint`、`npm run test`（92/92）、`npm run build`（Node 24）。
3. App：student-app `npm run build:h5`（uni-app 编译通过）。
4. 全栈 E2E：`python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080`（37 项 PASS：注册登录→采集传图→提交→教师审核→展廊/首页/详情→地图统计→评论评分→管理员下线重上架）。
5. V4 冒烟：`python -X utf8 scripts/plant-v4-smoke.py http://127.0.0.1:8080`（23 项 PASS：Actuator/防爆破/双端 dashboard/搜索/建议闭环/批量审核闭环/照片编辑 organ·cover·order）。
6. 重启持久化：重启 backend 后复查展廊/图片/审核历史仍在（E2E restart 分支）。
7. 图片/地图回归：nginx 下 /media/plants 图片与 /jingxuan/geo/*.json 均 200；MapLibre 页 /jingxuan/plant/map 可达。
8. CI（GitHub Actions）：backend-quality + plant-docker-e2e + 前端门禁全部绿后合并。

## 4. V4 冒烟修复记录（本发布说明随附）

- plant-v4-smoke.py 原两处失败均修复并固化断言：
  1) 批量审核样本必须先补全 speciesId/省市区/观察时间再 submit（此前漏 observedAt 导致停在 DRAFT，批量接口拒绝——现对 create/update/submit 各步显式 check）。
  2) 展廊断言改为 `sort=latest&size=20` 拉取（默认 featured 排序会把未精选新记录挤出首页）。
- 修复生产配置缺陷：application-prod.yml 中 HikariCP 参数原被误嵌套在 `spring.flyway.hikari` 下不生效，已移至 `spring.datasource.hikari`，并以 /actuator/metrics/hikaricp.connections.max=20 实测确认（池名 JingxuanPool）。
- 图片显示专项修复（2026-09-10）：① 植物照片上传曾复用旧域附件白名单（zip/rar/7z/jpg/png/gif/mp4/pdf），导致 **webp 与 jpeg 被拒**（用户从网页保存的图片多为 webp，上传直接失败）——已改为植物域白名单（jpg/jpeg/png/webp/gif）+ 魔数/尺寸防护，单测 424/424；② 演示环境真实照片入库：scripts/seed-demo-photos.py 从 Wikimedia Commons 取图（自由许可）并经完整审核链路公开，署名见 docs/demo-photo-credits.md；③ 历史冒烟/E2E 产生的 1×1 测试图（70B/633B）经 scripts/cleanup-demo-test-photos.py 由管理员强制下线（23 条），展廊/首页只保留真实图片；④ plant-v4-smoke.py 增加自清理，运行后自动下线自身测试记录。
- 本轮追加（发现即修）：① App 编辑页补全“设为封面/上移下移排序/单张器官标签”真实能力（后端新增 PUT /api/student/plant/observations/{id}/photos/{photoId}/organ，带越权与状态校验，单测 4 例）；② 公开植物页图片补描述性 alt（展廊卡/物种卡/地图抽屉/省面板/公开详情，ObservationCard 等 5 处）并过 typecheck/lint/vitest 92；③ 隐私开关接线 `plant.privacy.show-real-name: ${PLANT_SHOW_REAL_NAME:true}`（application.yml + .env.example + docker-compose 环境注入，容器实测 env=true）。

## 5. 已知限制（1.0 范围内接受）

- App 未申请定位权限（隐私设计）：地图/地点均为手选省市区，非 GPS。
- 评论/建议内容审核依赖 DEEPSEEK_API_KEY；未配置时按 profile fallback（dev bypass / prod reject）——生产必须配置。
- 3D 地图（WebGL fill-extrusion）在低端设备自动降级为列表视图；省界 GeoJSON 为 DataV 来源，正式公开展示前需按合规文档（docs/plant-map-data-source-and-compliance.md）替换为带审图号数据。
- 集成测试（*ApiTest/Testcontainers）仅 Linux+CI 可跑；本地 Windows 以 docker 栈 E2E/冒烟等价覆盖。

## 6. 相关文档索引

- 运维/数据/备份：docs/plant-platform-operations.md
- 生产部署/监控告警/安全核对/隐私策略/错误码与无障碍：docs/plant-platform-v4-production.md
- 学生手册 / 教师与管理员手册 / 试点说明：docs/plant-platform-manual-student.md、docs/plant-platform-manual-teacher-admin.md、docs/plant-platform-pilot.md
- App 打包：docs/student-app-android-build.md
