# 旧作品体系收口与下线评估（Legacy Work Retirement）

> 依据《全国植物观察平台_下一步开发计划_地图完善与旧功能收口》§5~§9、§14、§17。
> 本阶段目标：学生内容统一归入 PlantObservation（植物观察），前端旧作品入口全部下线；
> 后端旧 Work 按依赖顺序渐进停用（先前端、再接口、最后数据结构）。

## 1. 本阶段已完成（前端收口）

### 1.1 删除的页面与路由

| 类型 | 内容 |
|---|---|
| 学生端页面 | frontend/src/views/student/MyWorks.vue、WorkSubmit.vue |
| 公开端页面 | frontend/src/views/public/WorkList.vue、WorkDetail.vue |
| 对应测试 | student/__tests__/MyWorks.test.ts、WorkSubmit.test.ts；public/__tests__/WorkList.test.ts、WorkDetail.test.ts |
| 学生端路由 | /student/works、/student/works/create、/student/works/edit/:id、/student/works/view/:id |
| 公开端路由 | /works、/works/:id |

### 1.2 入口与文案统一

- 学生首页：主按钮改为「新增植物观察」→ /student/observations/create；次按钮「植物观察展廊」→ /plant/gallery；
  四张状态卡（草稿观察 / 待审核 / 已通过 / 被驳回）数据源由旧作品列表改为
  GET /api/student/plant/dashboard，点击进入 /student/observations。
- 学生端侧边菜单：移除「旧·作品管理」，保留「我的植物观察」；页面标题/描述映射同步更新。
- 学生端登录页、公开端布局：移除指向旧展廊的入口，统一指向植物观察展廊。
- 学生待办页：去提交 → /student/observations/create；「查看作品」→「查看我的观察」。
- 公开排行榜（旧域）：移除已下线的 /works/{id} 详情链接，改为纯文本展示作品名。
- 管理员评论页：旧作品详情链接改为植物观察展廊。

### 1.3 前端 API 模块收口

| 处理 | 模块 |
|---|---|
| 删除 | frontend/src/api/student/work.ts（旧作品 CRUD/提交/详情） |
| 删除 | frontend/src/api/public/work.ts（旧公开作品列表/详情/点赞）与其测试 |
| 删除 | frontend/src/api/workAdapter.ts 与 api/__tests__/adapter.test.ts |
| 新增 | frontend/src/api/upload.ts（通用文件上传，供个人资料等使用） |
| 保留 | api/teacher/work.ts（教师评分页仍在用）、api/admin/audit.ts（管理员审核页仍在用）、api/types.ts 中的 WorkListVO/WorkDetailVO、shared/api/generated/**（OpenAPI 生成客户端与契约门禁产物） |

## 2. 后端旧 Work 依赖评估（尚未删除，理由见 §4）

- 旧 work 模块源码：backend/src/main/java/com/jingxuan/modules/work/**（14 个文件）
- 全仓引用旧 work 包的位置：**22 个文件、48 处**，分布如下：
  - 服务层：WorkService / WorkMemberService / WorkServiceImpl / WorkMemberServiceImpl / WorkMemberPolicyService /
    DeleteRequestServiceImpl / RewardIssueServiceImpl
  - v1 契约 API：modules/work/web/V1ShowcaseController、V1PortfolioController、modules/work/api/V1*（DTO/明细）
  - 适配层：TeacherWorkFacade、PublicWorkFacade、StudentRankingFacade，以及 Admin/Public/Student/Teacher 四个 Adapter 控制器
  - 工作流：workflow/TaskWorkSubmissionWorkflow
- 仍在运行的旧域功能（因此这些接口暂时不能停）：教师评分（/api/teacher/work/**）、管理员内容审核
  （/api/admin/audit/**、评论管理）、公开排行榜、学生待办、删除申请、奖品发放。

## 3. 数据现状：无需迁移

| 表 | 记录数 | 结论 |
|---|---|---|
| work | 0（deleted=0） | 无历史作品数据 |
| work_attachment / work_audit / work_comment / work_like / work_member / work_publish / work_score / work_tag | 随主表为空 | 无内容需要迁移为 PlantObservation |

因此计划 §9 的「旧作品迁移」在本仓库当前数据下不适用；若生产环境存在真实旧数据，
迁移映射建议为：submitter_id → plant_observation.submitter_id，title → reported_common_name，
summary → description，cover_url/附件 → plant_photo，submit_time → observed_at，status → status，
region → province/city/district，featured → featured。

## 4. 为什么本阶段不物理删除后端旧 Work

1. **仍被旧域功能使用**：教师评分、管理审核、排行榜、待办、删除申请等页面的接口链仍依赖 WorkService 及其表；
   直接删除会导致这些页面 500。
2. **集成测试与 CI 门禁强依赖**：modules/adapter 测试中有约 100 处调用 /api/student/works、/api/public/works 等旧端点；
   CI 的 api-contract 与 legacy-runtime-smoke 任务同样基于旧域契约。删除后端必须同步重写这些测试与门禁任务，
   属于一次性大改动，应作为独立阶段评审后执行。
3. **计划本身要求渐进**（§8.1）：先删前端入口 → 确认统一到 PlantObservation → 确认旧接口无调用 → 再删后端 → 最后处理表。

本阶段已完成后端侧的**标记与冻结**：modules/work 包新增 package-info 说明收口状态，
WorkService 接口补充 Javadoc 指向本文档；旧模块不再新增功能。

## 5. 后端下线路线（下一阶段执行清单）

1. **确认零调用**：在网关/访问日志中确认 /api/student/works*、/api/public/works*、/api/public/showcase* 无流量；
   前端仓库内再次全局搜索 works（应为 0 处有效业务调用）。
2. **停用学生/公开侧接口**：移除 StudentApiController、PublicApiController 中旧作品路由（或统一返回 410 Gone），
   同步删除/改写 modules/adapter 中对应集成测试。
3. **迁移或归档旧域只读能力**：教师评分、管理审核这些仍要保留的能力，改为直接读取 PlantObservation
   （评分对象从 Work 切换为 PlantObservation），这一步完成后旧域即可整体退出。
4. **删除代码**：按 Controller → Facade → Service → Mapper → DTO/VO → Entity 顺序删除，最后删除
   modules/work 包与 workflow/TaskWorkSubmissionWorkflow。
5. **删除数据结构**：新增 Flyway 迁移（V15+）删除 work_* 表；在此之前保留一个只读快照（mysqldump）以防万一。
6. **同步清理**：legacy-runtime-smoke、api-contract 的 CI 任务与快照、OpenAPI 生成产物、旧域前端页面
   （teacher/score、admin/audit、public/Ranking 等）一并规划下线。

## 6. 风险与回滚

- 风险：旧域页面在删除 WorkService 后不可用；缓解：按 §5 顺序，先切换评分/审核对象再删表。
- 回滚：代码层面按提交回滚即可；数据层面在步骤 5 之前做整库备份（scripts/backup-plant.py 或 mysqldump）。
