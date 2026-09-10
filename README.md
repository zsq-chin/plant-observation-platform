# 全国植物观察与交流平台 (Plant Observation Platform)

[![CI](https://github.com/zsq-chin/plant-observation-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/zsq-chin/plant-observation-platform/actions/workflows/ci.yml)

面向中小学的**全国植物观察与交流平台**：学生用手机或浏览器采集身边的植物、上传照片并记录观察信息，教师审核后进入公开展廊与全国地图，形成可长期积累的校园植物图鉴。

- 公开端：植物展廊、观察详情、评论与评分、物种库、3D 中国植物地图（按省聚合）
- 学生端：Web + Android App 双入口，草稿-提交-审核闭环（无定位权限，地点手动三级选择）
- 教师端：审核工作台（单条/批量通过、驳回意见、质量提示）、物种建议处理、优秀观察推荐
- 管理员端：物种库与类别维护、违规记录强制下线/重新上架、图片健康与一致性巡检

> 本项目由一个校园作品展示平台改造而来（V2 平台化 → V3 图片本地化与 3D 地图 → V4 学生 App 与正式上线）。旧作品域（work/audit/score/rank/prize）代码与页面保留停用、未删除，详见 [docs/plant-platform-transformation.md](docs/plant-platform-transformation.md)。

---

## 功能特性

| 角色 | 功能 |
|---|---|
| 游客 / 公开端 | 植物展廊（关键词/省份/类别/年份/精选筛选与排序）、观察详情（多图、地点、动态描述项、教师审核意见、评论、星级评分）、物种库、全国 3D 植物地图（省级立体柱 + 城市节点 + 物种 TOP 榜）、全局搜索 |
| 学生（Web / App） | 采集植物观察：拍照或相册多选（最多 10 张，自动压缩）、器官标签、封面与排序、省市区手选、观察时间与描述、动态描述项；草稿保存（服务端 + App 本地草稿）、提交审核、撤回、被驳回后修改重提；我的植物（按状态分栏）、待鉴定（未知植物）、物种建议、消息通知 |
| 教师 | 植物观察审核中心：待审列表、详情查看、通过（可绑定标准物种）、驳回（必填意见）、数据质量提示（缺照片/缺省份/未选物种未标待鉴定/时间异常/疑似重复）、批量审核、设为优秀观察；新物种建议审批（通过即自动建档）；点评与评分 |
| 管理员 | 类别与物种库维护、观察记录治理（强制下线 / 重新上架）、图片健康与一致性巡检（只报告不自动删除）、平台统计 |

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1 / Java 21 / Spring Modulith（模块化单体）/ MyBatis-Plus 3.5.17 |
| 数据库 | MySQL 8（utf8mb4）+ Flyway V1–V12 增量迁移（只增不改） |
| 缓存与限流 | Redis 7（登录防爆破、评论防刷、令牌黑名单、刷新令牌族，键统一 jingxuan: 前缀） |
| 安全 | Spring Security + JWT、角色 RBAC、Actuator 端点收敛、图片魔数与尺寸（防图片炸弹）双重校验 |
| 内容安全 | DFA 敏感词 + DeepSeek 内容审核（生产 fallback=reject） |
| Web 前端 | Vue 3 + TypeScript + Vite 8 + Element Plus + Pinia，MapLibre GL 3D 地图 |
| 学生 App | uni-app（Vue3 + TS + Pinia），同一账号与后端，可打包 Android |
| 媒体存储 | 本地磁盘 data/plant-media（原图 + 320px 缩略图），Nginx 直出 /media/plants/** |
| 测试 | JUnit 5 + Mockito、Vitest、Testcontainers 集成测试、Python 全栈 E2E 与冒烟脚本 |
| 部署 | Docker Compose（MySQL + Redis + Backend + Nginx）/ PM2 + Nginx / GitHub Actions CI |

## 目录结构

```
├── backend/                       # Spring Boot 后端
│   ├── src/main/java/com/jingxuan/
│   │   ├── plant/                 # 植物域：controller/service/mapper/entity/dto/vo/storage/security
│   │   ├── modules/               # 旧作品域模块（保留停用）+ adapter 控制器
│   │   ├── common/ security/ config/ exception/
│   ├── src/main/resources/db/migration/   # Flyway V1–V12
│   └── src/test/java/com/jingxuan/plant/  # 植物域单测 + PlantPlatformApiTest（Testcontainers）
├── frontend/                      # Web 前端（公开端 / 学生端 / 教师端 / 管理端）
│   ├── src/views/public/          # 植物首页、展廊、详情、物种库、3D 地图
│   ├── src/views/student|teacher|admin/    # 我的植物、审核中心、物种建议、植物平台
│   └── src/modules/plant-map/     # 地图数据与组件
├── student-app/                   # 学生端 uni-app 工程（11 个页面）
├── scripts/                       # E2E/冒烟、真实照片种子、测试数据治理、备份恢复、数据导入
├── docs/                          # 改造说明、运维、上线基线、手册、试点、合规
└── docker-compose.yml / nginx-jingxuan.conf / ecosystem.config.cjs
```

## 快速开始（Docker，推荐）

```bash
git clone https://github.com/zsq-chin/plant-observation-platform.git
cd plant-observation-platform
cp .env.example .env      # 至少填写 DB_PASSWORD 与 JWT_SECRET（生产另填 DEEPSEEK_API_KEY / MAIL_*）
docker compose up -d --build
docker compose ps        # 期望 mysql/redis/backend/nginx 均 healthy
curl -fsS http://127.0.0.1:8080/actuator/health   # 期望 status=UP
```

| 服务 | 地址 |
|---|---|
| 前端（公开端首页） | `http://localhost/jingxuan/` |
| 植物展廊 / 3D 地图 | `http://localhost/jingxuan/plant/gallery`、`http://localhost/jingxuan/plant/map` |
| 后端 API / 接口文档 | `http://127.0.0.1:8080/`、`http://127.0.0.1:8080/swagger-ui.html` |
| 健康检查 / 指标 | `http://127.0.0.1:8080/actuator/health`（公开）、`/actuator/prometheus`（需管理员） |

### 演示账号（仅本地试用环境，生产请自行创建并改密）

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 管理员 |
| tea1 | admin123 | 教师（可审核） |
| stu1 / stu2 | admin123 | 学生 |

### 本地开发

```bash
# 后端（终端 1，JDK 21 + MySQL + Redis）
cd backend && mvn spring-boot:run          # 端口 8080

# Web 前端（终端 2，Node 24）
cd frontend && npm install && npm run dev  # http://localhost:5173，自动代理 /api

# 学生 App（H5 预览 / 构建）
cd student-app && npm install && npm run dev:h5
```

## 学生 Android App

`student-app/` 基于 uni-app（Vue3 + TS + Pinia），含登录、首页工作台、展廊、采集、编辑、我的植物、观察详情、全国地图、消息、我的、物种建议共 11 个页面；支持拍照/相册多选与压缩、器官标签、封面与排序、服务端草稿与本地草稿重试、待鉴定（未知植物）提交。
打包与签名步骤见 [docs/student-app-android-build.md](docs/student-app-android-build.md)，构建验证命令：`npm run build:h5`。

## 图片存储与媒体访问

- 存储约定：`data/plant-media/original/{observationId}/{uuid}.jpg` 与 `thumbnail/{observationId}/{uuid}.jpg`（320px 缩略图）
- 访问约定：`/media/plants/**` —— 容器部署由 Nginx 只读卷直出，后端亦提供静态映射；裸机部署需保留 `location /media/plants/` 规则
- 上传约束：格式 jpg/jpeg/png/webp/gif、单张 ≤10MB、边长 ≤10000px、像素 ≤5000 万；webp 不生成缩略图时回退原图
- 演示数据：`python -X utf8 scripts/seed-demo-photos.py --base http://127.0.0.1:8080` 可从 Wikimedia Commons 取真实植物照片并按完整审核链路入库，署名见 [docs/demo-photo-credits.md](docs/demo-photo-credits.md)
- 测试数据治理：`python -X utf8 scripts/cleanup-demo-test-photos.py --base http://127.0.0.1:8080`（`--purge` 彻底清理）

## 隐私与内容安全设计

- **不采集定位**：不使用 GPS/基站定位，地点由学生主动选择省/市/区县（GB/T 2260 代码）+ 手填文字；App 不申请定位权限，Nginx 禁用 geolocation
- **姓名可脱敏**：`plant.privacy.show-real-name`（环境变量 `PLANT_SHOW_REAL_NAME`）默认展示真实姓名，置 false 则按 张* / 欧**娜 规则脱敏（展廊、详情、评论、审核列表统一生效）
- **审核后公开**：仅 `status=APPROVED` 且 `is_public=1` 的观察进入展廊/地图/公开搜索；管理员可随时强制下线
- **滥用防护**：登录连续失败 5 次锁定 10 分钟、评论每分钟超 20 条拒绝、图片炸弹（超大尺寸/像素）拒收、接口限流
- **内容审核**：评论与物种建议先经敏感词与 AI 审核，生产环境审核失败即拒绝（不可绕过）

## 开发与运维命令

```bash
# 后端
cd backend
mvn compile                    # 编译
mvn test                       # 单元测试（Surefire）
mvn verify                     # 单元 + Testcontainers 集成测试（需 Docker，CI 上运行）

# Web 前端
cd frontend
npm run typecheck && npm run lint && npm run test && npm run build

# 学生 App
cd student-app && npm run build:h5

# 全栈回归（需要已启动的本地栈）
python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080    # 主闭环 E2E
python -X utf8 scripts/plant-v4-smoke.py http://127.0.0.1:8080    # V4 能力冒烟（含自动清理）

# 数据与运维
python -X utf8 scripts/import-plant-regions.py --districts --apply   # 行政区数据导入
python -X utf8 scripts/migrate-plant-images.py                      # 旧图片迁移到本地存储
python -X utf8 scripts/backup-plant.py backup/plant-media           # 备份（含 manifest）
python -X utf8 scripts/restore-plant.py --backup-dir backup/plant-media
```

## 测试与质量门禁

| 项目 | 结果 |
|---|---|
| 后端单元测试（Surefire） | 424 / 424 通过 |
| 后端集成测试（Testcontainers） | 在 CI（Linux + Docker）执行，见 `backend-quality` 任务 |
| Web 前端 | typecheck / lint 通过，Vitest 92 通过（24 既有跳过） |
| 全栈 E2E | 37 项通过（注册登录 → 采集传图 → 提交 → 审核 → 展廊/首页/详情 → 地图 → 评论评分 → 下线/上架） |
| V4 冒烟 | 25 项通过（Actuator、防爆破、双端工作台、搜索、物种建议、批量审核、照片器官/封面/排序） |
| 学生 App | `npm run build:h5` 构建通过 |
| CI 门禁 | `.github/workflows/ci.yml`：前端质量、后端单测+集成、plant-docker-e2e（全栈 E2E 与冒烟） |

## 数据库迁移（Flyway 只增不改）

| 版本 | 内容 |
|---|---|
| V1–V6 | 原作品域基线、注册班级选项等 |
| V7 | 植物域核心：sys_region、plant_category、plant_species、plant_observation、plant_photo |
| V8 | 动态描述项：plant_field_definition / plant_field_value |
| V9 | plant_review（审核留痕）、plant_comment、plant_rating |
| V10 | 种子数据：34 省级行政区、4 个植物类别、5 个标准物种 |
| V11 | 行政区可视化字段（center/min/max） |
| V12 | 物种建议表 plant_species_suggestion + 观察 identification_status（待鉴定） |

观察状态机：`DRAFT → SUBMITTED → APPROVED / REJECTED`，`REJECTED` 可修改重提，`APPROVED → OFFLINE`（管理员下线）；每次审核写 `plant_review` 留痕。

## 环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DB_ROOT_PASSWORD` / `DB_PASSWORD` | MySQL root / 应用账号密码 | — |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符随机值） | — |
| `DEEPSEEK_API_KEY` | 内容审核密钥（生产必填） | — |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | 注册验证码邮件 | `smtp.qq.com` / `587` |
| `PLANT_STORAGE_ROOT` | 图片存储根目录 | `./data/plant-media` |
| `PLANT_SHOW_REAL_NAME` | 公开端姓名策略（true 显示真名 / false 脱敏） | `true` |
| `JINGXUAN_SECURITY_TRUSTED_PROXY_CIDRS` | 可信反向代理 CIDR | `127.0.0.1/32,::1/128` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 | `localhost` / `6379` |

`.env` 含真实凭据，已被 `.gitignore` 排除，严禁提交。

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/plant-platform-transformation.md](docs/plant-platform-transformation.md) | 平台改造实现说明（V2 基线） |
| [docs/plant-platform-v4-release-notes.md](docs/plant-platform-v4-release-notes.md) | V4 发布说明、RC 冻结与回归清单、修复记录 |
| [docs/plant-platform-v4-production.md](docs/plant-platform-v4-production.md) | 生产部署（HTTPS/Nginx/环境分层）、监控告警、安全核对、隐私与无障碍 |
| [docs/plant-platform-operations.md](docs/plant-platform-operations.md) | 运维：备份恢复、图片一致性、演示数据准备、测试数据治理、排障 |
| [docs/plant-platform-manual-student.md](docs/plant-platform-manual-student.md) | 学生用户手册（Web + App） |
| [docs/plant-platform-manual-teacher-admin.md](docs/plant-platform-manual-teacher-admin.md) | 教师与管理员手册 |
| [docs/plant-platform-pilot.md](docs/plant-platform-pilot.md) | 1.0 试点运行说明 |
| [docs/student-app-android-build.md](docs/student-app-android-build.md) | 学生 App 构建与 Android 打包 |
| [docs/plant-map-data-source-and-compliance.md](docs/plant-map-data-source-and-compliance.md) | 地图边界数据来源与合规 |
| [docs/demo-photo-credits.md](docs/demo-photo-credits.md) | 演示照片署名与许可 |
| [AGENTS.md](AGENTS.md) | 面向开发者的仓库结构与约定 |

## 与旧作品域的关系

原平台的 work / audit / score / scorebatch / rank / prize 等模块与数据表保留（前端标注「旧」入口），但不再是主线业务；植物域为 `com.jingxuan.plant.*`，通过 `/api/public|student|teacher|admin|community/plant/**` 提供接口。

## 安全注意

- `.env`（数据库密码、JWT 密钥、DeepSeek Key、邮箱授权码）严禁入库；仓库仅保留 `.env.example` 占位模板
- 生产环境务必更换 JWT_SECRET 与数据库口令，启用 HTTPS，并限制 8080/3306/6379 仅本机或内网访问
- 上传文件经扩展名白名单 + 真实魔数 + 尺寸/像素上限三重校验；公开内容先审后发
- 管理员可随时对违规观察执行强制下线（移出展廊、地图与公开搜索）

## 素材与数据来源

- 演示植物照片来自 Wikimedia Commons 自由许可作品（作者与许可见 [docs/demo-photo-credits.md](docs/demo-photo-credits.md)），对外发布需保留署名或替换为学校自摄照片
- 省级边界 GeoJSON 为 DataV 公开数据，正式公开展示前请按合规文档替换为带审图号数据
