# 全国植物观察与交流平台 — 改造实现说明

> 依据《全国植物观察与交流平台_详细改造清单_V2_含首页植物展廊.md》与《详细设计说明书.docx》，
> 在 jingxuan（学院作品展示平台）代码基座上增量改造。分支：feature/plant-platform。

## 1. 总体原则

- 不推翻基建：复用登录/JWT、班级/用户、Result/PageResult、上传白名单+魔数校验、敏感词+DeepSeek 审核、Flyway、Springdoc、测试体系。
- 旧域保留停用：work/audit/score/rank/prize/scorebatch 等旧代码与旧表保留（前端标注「旧·/旧版」入口），新系统稳定后再清理。
- 物种与观察分离：plant_species（标准物种）与 plant_observation（学生一次观察记录）1:N 分离。
- 隐私约束：不获取学生设备定位/GPS/家庭地址；地点由学生主动选择 省/市/区县（GB/T 2260 代码），详细地点手填；Nginx 已带 geolocation() 禁用头。
- 审核决定展示：仅 status=APPROVED 且 is_public=1 的数据进入首页展廊/公开详情/中国地图/公开搜索。

## 2. 数据库（Flyway V7-V10，紧接既有 V6）

| 版本 | 内容 |
|---|---|
| V7 | sys_region（34 省级+示例市区）、plant_category、plant_species、plant_observation（含展廊字段 featured/published_at/is_public/view_count 等）、plant_photo |
| V8 | plant_field_definition / plant_field_value（教师动态描述项） |
| V9 | plant_review（审核历史）、plant_comment（评论/回复/置顶/隐藏）、plant_rating（UNIQUE(observation,user) 1~5 星） |
| V10 | 种子：34 个省级行政区、4 个植物类别、5 个标准物种（银杏/珙桐/牡丹/狗尾草/爬山虎） |

观察状态机：DRAFT -> SUBMITTED -> APPROVED/REJECTED；REJECTED 可修改重提；APPROVED -> OFFLINE（管理员下线）；每次审核写 plant_review 留痕。

## 3. 后端（com.jingxuan.plant.*）

公开（无需登录，只返回审核通过且公开）：
- 区域：GET /api/public/plant/regions/provinces、/regions/{parentCode}/children
- 物种：/species/search、/species/{id}；类别 /categories
- 展廊/首页/详情：/gallery（关键词/省/类别/班级/年份/精选/排序）、/home（统计+精选+最新+教师点评）、/observations/{id}（浏览量+1）、/observations/{id}/comments
- 地图：/map/china（省份聚合）、/map/provinces/{provinceCode}/species、/map/species/{speciesId}/observations

学生（/api/student/plant/**，STUDENT/ADMIN）：
- 观察记录草稿 CRUD、提交（服务端校验：>=1 张照片、省份必填、植物或上报名称、观察时间、必填动态项）、撤回、删除；越权一律拒绝（submitter 取自 JWT）
- 照片：上传（图片白名单+魔数+<=10 张+封面规则）、删除、设封面、排序；动态描述项列表

教师（/api/teacher/plant/**，TEACHER/ADMIN）：
- 审核列表/详情/通过（可绑定标准物种）/驳回（必填意见）/历史/推荐优秀观察
- 动态描述项配置（新增/修改/启停；停用不物理删除，版本递增保留历史值）

管理员（/api/admin/plant/**，ADMIN）：类别/物种维护、观察记录治理（强制下线/重新上架）
社区（/api/community/plant/**，登录用户）：评论/回复（parent_id 线程）、教师点评置顶、隐藏、删除（作者/管理员）、1~5 星评分 upsert；评论先过 DFA+DeepSeek 内容审核。

## 4. 前端

公开端（默认首页即植物首页）：
- /plant 首页：统计卡片、最新教师点评、优秀观察、最新展廊、地图/物种库入口
- /plant/gallery 植物观察展廊（筛选+分页）；/plant/observations/:id 详情（多图/地点/学生/动态字段/审核意见/评论/评分）
- /plant/map 全国植物地图（省份聚合卡片）；/plant/species 物种库

学生端：/student/observations 我的植物观察（状态 Tab）与 /student/observations/create|edit/:id 采集/补充（先草稿后补全、三级区域联动、动态表单渲染、多图上传）
教师端：/teacher/reviews 植物观察审核中心（列表+详情对话框+通过/驳回/设为优秀）
管理员端：/admin/plant 植物平台（类别/物种库维护）

旧作品相关页面保留，菜单标注「旧·/旧版」。

## 5. 验证

- 后端单测：405/405 通过（含植物域观察/审核/社区规则 14 例），命令 npm run backend:test:unit（本机无 mvn 时可用 docker maven 镜像运行）
- 集成测试：com.jingxuan.plant.PlantPlatformApiTest（6 用例，覆盖主闭环/越权/治理），CI（Testcontainers）执行 npm run backend:test:integration
- 前端：vue-tsc / ESLint / Vitest（78 通过，Node 24）/ 生产构建 全部通过
- OpenAPI：api:check 语义契约链全绿（已发布 V1 101 操作与实时规格一致；快照与 orval 客户端已按管线再生成）
- 实机闭环：python -X utf8 scripts/plant-e2e-loop.py http://127.0.0.1:8080 —— 29 项断言（草稿→传图→提交→审核→精选→展廊/首页/地图→评论/评分→下线/上架）全部通过

## 6. 演示账号（本机 docker 栈）

admin/admin123（管理端，首登需改密）、tea1/admin123（教师）、stu1|stu2/admin123（学生，班级 1班）。班级种子 1班/2班/3班由 V6 提供。

## 7. 后续（按文档分阶段）

- P2+：教师工作台统计、动态字段体验完善、评论治理管理页扩展
- P3：AI 识别/规则辅助（表与接口已预留 plant_review.ai_reference 与识别服务抽象位）
- P4：学生 uni-app（Android）客户端、对象存储/缩略图服务、正式中国地图 GeoJSON 合规接入（自然资源部标准地图+审图号）
