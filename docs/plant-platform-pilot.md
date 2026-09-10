# 1.0 试点运行说明（全国植物观察与交流平台）

> 适用版本：全国植物观察与交流平台 1.0（分支 `release/plant-platform-1.0-rc1`）
> 本文是**试点（pilot）运行方案**：从“功能验收通过”到“正式上线”之间的组织化试运行手册，供学校管理员、试点教师、平台运维共同执行。
> 关联文档：`docs/plant-platform-manual-teacher-admin.md`（教师与管理员操作手册）、`docs/plant-platform-manual-student.md`（学生手册）、`docs/plant-platform-transformation.md`（改造实现说明/验收基线）、`docs/plant-platform-operations.md`（运维说明）、`docs/plant-platform-v4-production.md`（V4 上线基线：HTTPS/环境分层/监控告警/安全核对/隐私策略）、`docs/plant-platform-v4-release-notes.md`（发布说明）、`docs/plant-map-data-source-and-compliance.md`（地图数据合规）。

---

## 1. 试点目标与范围

### 1.1 试点目标

1. **验证真实教学闭环**：学生“采集 → 提交 → 教师审核 → 公开展示 → 互动点评”在真实班级、真实课表下跑通，测量审核闭环时长。
2. **验证内容与审核质量**：检验教师审核规范、新物种建议建档、优秀观察与教师点评机制能否产出高质量公开内容。
3. **验证治理与安全**：管理员治理流程（下线/上架、图片健康检查、备份恢复）、评论自动审核、防滥用防线在真实流量下是否有效。
4. **沉淀运营 SOP**：周任务主题、审核节奏、统计口径、周报模板，为转正式上线提供依据。
5. **暴露缺陷清单**：以真实使用收集功能缺口与缺陷（RC1 已知若干能力仅接口级，见操作手册 §6.6 对照表），决定正式版补做范围。

### 1.2 建议范围

| 维度 | 建议取值 | 说明 |
|---|---|---|
| 试点班级数 | **1~3 个班**（推荐 2 个班起步） | 覆盖“多班同主题、横向比较”，又不至于审核积压 |
| 学生规模 | 每班 30~50 人为宜，总 60~150 人 | 控制每日提交量在教师可审范围（见 3.3 节奏） |
| 教师 | 每班 1 名带班教师 + 1 名学科教师（生物/科学）共 2~4 名 | 学科教师负责鉴定正确性，带班教师负责督促提交 |
| 管理员 | 1 名平台管理员 | 账号/班级、物种库、治理、备份 |
| 时长 | **4 周主试点 + 1~2 周复盘**（建议 6 周整） | 4 周覆盖 4 个任务主题 + 完整评审 |
| 验收基线 | 见 §5.2 与 `plant-platform-transformation.md` §5 | E2E 主闭环、PlantPlatformApiTest、OpenAPI 契约等 |

### 1.3 初始数据基线（环境种子，可参考）

- 34 个省级行政区（GB/T 2260 码）+ 示例市区；4 个植物类别；5 个标准物种（银杏、珙桐、牡丹、狗尾草、爬山虎）——**试点第一周建议先扩充到覆盖校园常见植物 20~40 种**（管理端「植物平台」或教师“建议建档”均可加）。
- 班级种子：1班/2班/3班（既有 V6 迁移）。演示账号：`admin / admin123`、`tea1 / admin123`、`stu1、stu2 / admin123`（stu1/stu2 属 1班）。

---

## 2. 上线前检查单

> 每一项都有对应操作位置/命令。全部完成后，由管理员与试点教师共同签字进入试点。

### 2.1 环境与部署

- [ ] 后端 + MySQL + Redis + Nginx 整栈启动：
  ```bash
  docker compose up -d --build backend
  docker compose ps            # backend / mysql / redis / nginx 均应为 running/healthy
  ```
- [ ] 环境变量就位：`JWT_SECRET`（≥32 字符，上线必须替换默认值）、`DB_PASSWORD`、`DEEPSEEK_API_KEY`（评论/建议 AI 审核用，prod profile 缺失时按 reject 策略处理）、`MAIL_*`（如启用邮件）。参考项目根 `.env` 与 `AGENTS.md`。
- [ ] 冒烟与主闭环回归全绿：
  ```bash
  bash scripts/smoke-test.sh http://localhost:8080
  python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080     # 草稿→传图→提交→审核→精选→展廊/首页/地图→评论/评分→下线/上架
  python -X utf8 scripts/plant-v4-smoke.py                          # Actuator/防爆破/dashboard/搜索/建议/批量审核（可用 PLANT_BASE 覆盖地址）
  ```
  期望：全部输出 [PASS]、无 [FAIL] 且命令退出码为 0。
- [ ] （变更后可选）后端 `npm run backend:verify`（Testcontainers 需 Docker）、前端 `npm run frontend:test`、契约 `npm run api:check`。

### 2.2 管理员：建班与账号

- [ ] 核对/创建试点班级：管理端「用户管理」「数据字典」确认试点班级存在（可沿用 1班/2班/3班或按学校命名扩充）。
- [ ] 创建学生账号并归入班级：试点开始前统一下发账号密码并提醒**首登改密**。
- [ ] 创建/核对教师账号：试点教师角色为 TEACHER（如 `tea1`），确认能进入「植物观察审核」「新物种建议」两个菜单。
- [ ] 管理员账号可用：先按附录 A 取一次 token 并调通治理接口，确认权限链路。

### 2.3 教师与管理员配置

- [ ] 初始化物种库与类别：管理端「植物平台 → 植物类别/植物物种库」补充试点主题所需类别与**校园常见植物**（含学名、科属、介绍），停用无关演示物种（可选）。
- [ ] 动态描述项口径确认：观察记录动态字段（接口级 `/api/teacher/plant/plant-fields`）是否覆盖试点记录要素（如生活型/花色/叶形/生境），与学科教师确认后再开放。
- [ ] 审核分工约定：多教师试点须明确“谁审哪个班/哪个主题”，避免同一批记录重复处理或无人处理。
- [ ] 隐私配置确认：公开处姓名是否展示真实姓名由 `plant.privacy.show-real-name` 决定（RC1 未显式配置时默认展示真名；系统内置掩码函数：1 字→“*”、2 字→“张*”、3 字及以上→保留首尾中间以 * 代替，如“张三明→张*明”）。**面向未成年人请与学校确认后显式配置为 false 再对外公开**。
- [ ] 通知与沟通渠道：确认教师端「消息通知」可用，试点群同步建立用于日常催办与答疑。

### 2.4 公开访问 / 域名 / HTTPS

- [ ] 路由核对：Nginx 对 `/`（SPA）、`/api/*`、`/api/file/*`、`/uploads/*`、`/media/plants/*`（植物图片）反代正确；图片目录 `./data/plant-media` 由后端与 Nginx 共享（见 `docs/plant-platform-operations.md`）。
- [ ] 域名与证书：正式对外建议域名 + HTTPS；试点期如仅在校园内网/白名单开放需明确范围并记录（仓库 ADR-008 记录了 HTTP 风险接受决策，**转正式前应改为 HTTPS**）。HTTPS/Nginx 与反向代理的核对项、监控告警、Staging 分层策略详见 `docs/plant-platform-v4-production.md`。
- [ ] 安全头与缓存：`nginx-jingxuan.conf` 已含 CSP/X-Frame-Options 等头；图片 URL 有 30 天缓存，配置改动后 `nginx -s reload`。
- [ ] 知情同意：向家长/学生说明照片用于公开展示、不采集 GPS 定位，取得同意后再开公开访问。

### 2.5 备份策略（试点期即执行）

- [ ] 一键备份脚本就绪并验证：
  ```bash
  python -X utf8 scripts/backup-plant.py          # 默认输出 ../backup/YYYYMMDD-HHMMSS
  python -X utf8 scripts/backup-plant.py ../backup/pilot-2026-07-20   # 或指定目录
  ```
  备份内容：MySQL 全量 dump（--single-transaction）+ `data/plant-media` 图片目录 + `manifest.json`（记录恢复命令）。
- [ ] 频率策略：试点期**每日一次**全量备份（写入计划任务）；重大变更（迁移、清理演示数据、批量维护物种库）前后各一次。
- [ ] 恢复演练 ≥1 次（管理员执行）：
  ```bash
  python -X utf8 scripts/restore-plant.py <备份目录>
  ```
  恢复后必做：抽查公开端图片 URL 可访问；调 `/api/admin/plant-media/consistency` 确认 DB↔磁盘一致。
- [ ] 保留策略：至少保留最近 7 份 / 30 天，并异机拷贝一份。

### 2.6 演示数据清理策略

试点开始前清理开发/验收阶段的**演示数据**（演示观察、建议、评论、评分等），避免污染试点统计与公开内容：

- [ ] 清理范围决策：**保留**基础种子（34 省区划、类别、标准物种、班级）；**清理**演示产生的观察记录/照片、物种建议、评论、评分、精选标记；演示账号可禁用或保留但试点统计排除。
- [ ] 清理方式（按环境三选一）：
  1. **全新环境重灌种子**（最干净）：重建数据库 + Flyway 迁移 + 种子数据；
  2. **数据库清理**：删除业务表数据（plant_observation / plant_photo / plant_review / plant_comment / plant_rating / plant_species_suggestion），并同步清理 `data/plant-media` 对应图片目录；
  3. **归档后清理**：先 `python -X utf8 scripts/backup-plant.py` 备份归档，再按方式 2 清理。
- [ ] 清理后验证：`/api/public/plant/home` 统计归零或仅含预期种子；跑一次 consistency 扫描确认无孤儿/缺文件；教师审核列表无残留“待审核”。
- [ ] 试点期间**禁止再用演示账号（stu1/stu2）灌数据**；演示走 E2E 脚本并在独立测试环境执行（`python -X utf8 scripts/plant-e2e-loop.py`）。

### 2.7 内容与合规

- [ ] 地图合规：当前边界 GeoJSON 为 DataV 来源（见合规文档）；正式公开展示前按自然资源部标准地图 + 审图号要求更换；试点期可仅内网使用或暂不公开展示地图页。
- [ ] 照片授权与班会说明（不采集 GPS、地点主动选择、照片公开范围）。
- [ ] 内容防线默认开启（评论 ≤1000 字 + 敏感词/AI 审核 + 20 条/分钟限频；图片格式/大小/像素限制；登录 5 次失败锁 10 分钟），无需额外配置；AI 审核依赖 `DEEPSEEK_API_KEY` 可用，请提前验证。

---

## 3. 试点期运营节奏

### 3.1 角色分工

| 角色 | 每周固定动作 | 频次 |
|---|---|---|
| 学生 | 按周主题完成 1~2 条观察：拍照 → 补全描述 → 绑定物种或建议新物种 → 提交 | 每周 |
| 带班教师 | 催交与答疑；每天固定时段进「植物观察审核」处理本班待审；驳回写清修改意见 | 每日 |
| 学科教师 | 复核鉴定正确性；对优秀观察**设为优秀 + 教师点评（可置顶）**；处理「新物种建议」 | 每周 2~3 次 |
| 管理员 | 物种库/类别维护；每周一次治理巡检与备份；处理教师上报的下线诉求；跑图片 health/consistency | 每周 |
| 平台运维 | 版本发布、监控、备份恢复演练、故障响应 | 按需 |

### 3.2 每周任务主题建议（4 周主试点）

| 周 | 主题 | 任务建议 | 学科要点 | 审核关注点 |
|---|---|---|---|---|
| W1 | **校园植物图鉴**（入门） | 每人 1 条：拍 1 种校园植物 ≥1 张特征照，填名称/地点/描述 | 观察方法、植物器官入门 | 照片清晰度、地点合规、重复提交 |
| W2 | **行道树与园林植物** | 识校园/社区常见树：叶形、树皮、花果记录；优先绑定标准物种 | 检索与鉴定方法 | 建议建档质量、鉴定正确性 |
| W3 | **药草园 / 阳台与菜园植物** | 观察可食用/药用植物 1~2 条，鼓励补充动态字段 | 分类特征、安全提醒（不采食不明野果） | 动态字段完整性、描述真实性 |
| W4 | **秋色与物候观察** | 同株植物对比观察/物候现象（落叶、果实），可做“同物种多地对比” | 物候记录、数据可比性 | “疑似重复”提示较多时引导做对比而非重复提交 |

> 每周主题可由试点教师组替换为本地特色主题。**每周末**带班教师统计：提交数、待审数、驳回率，填入周报（模板见附录 B）。

### 3.3 审核节奏 SOP（教师）

1. **每日固定时段**（建议下午放学前 30 分钟）进入「植物观察审核」，先看页面顶部统计条：**待审 / 今日提交 / 今日通过 / 今日驳回**。
2. 待审 ≤ 20 条：单条处理（通过/驳回 + 意见）；> 20 条且问题同质：先抽查 3~5 条确认问题模式，再「批量驳回（统一意见）」或「批量通过」。
3. 优秀内容：当周挑选 2~5 条**设为优秀**，并由学科教师写 1 条教师点评（置顶需接口，见操作手册 §6.4）。
4. 建议队列：每周至少清空 1 次「新物种建议」；把握大的“通过（自动建档）”，把握小的驳回并说明或交管理员在物种库统一维护。

### 3.4 管理员每周巡检

```bash
# 每周固定巡检（管理员执行，Linux/Git Bash）
python -X utf8 scripts/backup-plant.py                 # ① 备份

# ② 获取管理员令牌：登录接口返回 JSON 的 data.token，将其粘贴到下方 $TOKEN
curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","rememberMe":false}'
TOKEN=<粘贴上面返回的 data.token>
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/plant-media/health       # ③ 图片存储健康
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/plant-media/consistency # ④ 一致性（只报告不删除）
```

巡检清单：备份成功？health/consistency 正常？教师上报的下线诉求已处理？评论自动审核是否有误伤（抽查）？公开首页“最新教师点评”是否在更新？

---

## 4. 师生常见问题速查表

| 现象 | 原因 | 处理 |
|---|---|---|
| 登录提示“登录失败次数过多，请10分钟后再试” | 连续 5 次输错密码触发账号级锁定（Redis 计数） | 等 10 分钟再试；不要反复尝试 |
| 学生提交的观察不进展廊 | 记录仍是草稿/待审核/被驳回，或已被管理员下线 | 教师进「植物观察审核」处理；只有 APPROVED 且公开才展示 |
| 学生看到“已驳回” | 教师驳回 | 学生进“我的植物观察”读审核意见，修改后重新提交 |
| 驳回时没写意见被拦 | 系统强制驳回必填意见 | 教师补写意见后再驳回 |
| 学生找不到要观察的植物 | 标准物种库未收录 | 学生提“新物种建议”；教师确认后自动建档或驳回说明 |
| 评论发不出去 | 未过敏感词/AI 审核，或 1 分钟超 20 条 | 修改内容重发或稍后再试；误伤可反馈管理员复核 |
| 照片传不上 | 仅 JPG/JPEG/PNG/WebP/GIF；≤10MB；单边 ≤10000 像素；像素总量 ≤5000 万；单条 ≤10 张 | 压缩/裁剪/改格式后重传 |
| 已通过的记录突然看不到 | 可能被管理员强制下线（移出公开数据，不删图） | 管理员查记录状态与下线原因后处理 |
| 教师想撤回刚通过的记录 | 教师无撤回能力；APPROVED 后改图需“下线—整改—重审” | 联系管理员下线后再处理 |
| 地图页打不开/显示异常 | WebGL 不可用时自动降级二维列表；边界 GeoJSON 合规替换未完成 | 试点期按 2.7 控制使用范围 |
| 图片 404 | 文件缺失/目录未共享/缓存未清 | 管理员跑 /health 与 /consistency，按运维文档排查 |

---

## 5. 试点结束评审与转正式上线条件

### 5.1 评审指标体系（第 4 周末汇总）

| 类别 | 指标 | 达标参考 | 数据来源 |
|---|---|---|---|
| 审核闭环 | 提交后 **24 小时内**被处理（通过/驳回）占比 | ≥ 90% | plant_review 审核时间 vs submit_time（DB 统计） |
| 审核闭环 | 每周五下班时“待审”积压 | 趋近 0 | `/api/teacher/plant/dashboard` |
| 内容质量 | 一次通过率（首次提交即通过占比） | ≥ 70% | DB 统计 |
| 内容质量 | 驳回原因 TOP3；缺照片/缺省份/未选物种未标待鉴定/疑似重复等质量提示出现率 | 逐周下降 | 驳回意见 + qualityWarnings |
| 内容质量 | 优秀观察/已通过占比、教师点评（置顶）条数 | ≥ 5% / 每周 ≥ 2 条 | /home、featured 字段 |
| 物种库 | 新增标准物种数；建议处理率（7 天内处理完） | 覆盖校园主要植物、建议不积压 | species_suggestion |
| 互动 | 每条公开记录平均评论数、评分人数 | 首周 vs 末周呈上升 | /home、详情接口 |
| 稳定性 | 运行期故障数、图片 health/consistency 异常数 | 阻断级故障 0 | 巡检记录 |
| 缺陷 | 试点缺陷清单：P1（严重）清零、P2（一般）有修复计划 | 全部登记并闭环 | 缺陷登记表 |
| 回归 | 试点前后 e2e / v4-smoke / 冒烟脚本 | 全部通过 | 见 §2.1 命令 |

> 口径提醒：试点统计**排除演示账号（stu1/stu2）**数据，清理策略见 2.6。

### 5.2 转正式上线条件（全部满足才切换）

**功能与缺陷**

- [ ] 试点缺陷 P1 清零，P2 均有修复版本与回归记录；
- [ ] RC1 仅接口级的能力按需补界面：记录治理（下线/上架）、图片 health/consistency 巡检页、评论隐藏/点评置顶按钮、审核详情“绑定标准物种 + 质量提示”展示（对照操作手册 §6.6）；
- [ ] 教师端审核视图（按班级分派等）在试点中发现的问题有明确处理方案。

**数据与治理**

- [ ] 试点数据归档/清洗方案确定（试点记录保留或迁入正式库，正式开服前演示数据清理完成）；
- [ ] 备份自动化 + 恢复演练 SOP 固化（每日自动备份已稳定运行 ≥2 周）；
- [ ] 隐私掩码配置（`plant.privacy.show-real-name`）按学校要求设定；评论 AI 审核误伤率可接受。

**环境与合规**

- [ ] 正式域名 + HTTPS 就绪（替换内网 HTTP，关闭 ADR-008 的 HTTP 风险接受项）；
- [ ] 地图 GeoJSON 换用合规底图（标准地图 + 审图号），或决定正式版暂缓公开展示地图；
- [ ] 容量与性能按试点峰值 × 学校规模预留（参考性能测试报告）；
- [ ] 培训材料（操作手册、学生端帮助）更新为正式文案并完成宣讲。

**验收**

- [ ] 正式发布前回归链全绿：
  ```bash
  bash scripts/smoke-test.sh http://localhost:8080
  python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080
  python -X utf8 scripts/plant-v4-smoke.py
  ```
- [ ] 与既有验收呼应：主闭环场景（草稿→传图→提交→审核→精选→展廊/首页/地图→评论/评分→下线/上架）、PlantPlatformApiTest 集成测试、OpenAPI 契约（`api:check`）保持全绿（基线见 `docs/plant-platform-transformation.md` §5）。

### 5.3 评审会议输出物

1. 试点周报 ×4（模板见附录 B）+ 汇总评审表（5.1 指标）；
2. 缺陷登记表（严重级、复现步骤、修复版本、回归结果）；
3. 教师反馈清单（界面/流程/字段口径改进建议）；
4. “转正式上线检查单”勾选结果，本文档归档为试点决策记录。

---

## 附录 A：命令速查（管理员/运维）

```bash
# 启动与状态
docker compose up -d --build backend
docker compose ps

# 上线前回归（建议三条全绿）
bash scripts/smoke-test.sh http://localhost:8080
python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080
python -X utf8 scripts/plant-v4-smoke.py                        # 可用环境变量 PLANT_BASE 覆盖地址

# 含后端容器重启的持久化校验（回归时可选）
PLANT_E2E_RESTART=1 python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080

# 备份 / 恢复
python -X utf8 scripts/backup-plant.py                          # 默认 ../backup/YYYYMMDD-HHMMSS
python -X utf8 scripts/restore-plant.py <备份目录>

# 旧图迁移（如存在 /uploads/plant/** 遗留）与行政区数据重导
python -X utf8 scripts/migrate-plant-images.py
python -X utf8 scripts/import-plant-regions.py --districts --apply

# 管理员治理与图片检查（先按 §3.4 获取 $TOKEN）
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/plant-media/health
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/plant-media/consistency
curl -X POST -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/plant/observations/<id>/offline
curl -X POST -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/plant/observations/<id>/online
```

> 命令默认在仓库根目录、Linux/Git Bash（WSL）下执行；Windows 本地验证请改用 PowerShell 或 WSL。数据库手工备份/图片目录冷备详见 `docs/plant-platform-operations.md`。

---

## 附录 B：试点周报模板

| 项目 | W1 | W2 | W3 | W4 | 备注 |
|---|---|---|---|---|---|
| 提交观察数（学生，排除演示账号） |  |  |  |  |  |
| 教师处理数（通过/驳回） |  |  |  |  |  |
| 周五待审数 |  |  |  |  |  |
| 24 小时内处理占比 |  |  |  |  |  |
| 驳回率 / 驳回原因 TOP3 |  |  |  |  |  |
| 设优秀数 / 教师置顶点评数 |  |  |  |  |  |
| 新物种建议：新增/处理/积压 |  |  |  |  |  |
| 图片 health / consistency |  |  |  |  |  |
| 缺陷新增/关闭（P1/P2） |  |  |  |  |  |
| 下周主题与分工 |  |  |  |  |  |
