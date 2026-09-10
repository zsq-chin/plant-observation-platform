# 移动端提交审核 · 图片访问 · 地图与台湾省修复记录

> 依据《全国植物观察平台_移动端审核图片地图台湾省_详细修复方案》执行。
> 目标：先把“手机端提交 → 教师端审核 → 图片正确展示”闭环打通，再做地图完整性（台湾省）与手机端 2D 地图。

## 1. 关键业务异常不再被吞掉（P0-1）

| 位置 | 修复前 | 修复后 |
|---|---|---|
| student-app observation-edit（补图上传） | uploadPhoto(...).catch(() => undefined) 静默失败 | 逐张上传并收集失败项，结束时提示「有 N 张图片上传失败」，成功才提示已上传 |
| observation-edit（设封面 / 排序 / 删除照片） | catch(() => undefined) | try/catch + 失败提示；排序失败会回滚本地顺序，避免与服务端不一致 |
| my-observations（提交审核 / 撤回 / 删除） | catch(() => undefined) 后仍提示「已提交」 | 只有接口返回成功才提示；失败保持原状态并提示原因 |
| my-observations（本地草稿同步上传） | 图片失败被忽略且删除本地草稿 | 收集失败项，失败时**保留本地草稿**并提示未同步数量 |
| suggestion（提交物种建议） | 成功后清空表单、失败无反馈 | 成功才清空；失败保留用户输入 |

原则：上传 / 提交 / 保存 / 删除 / 撤回等关键写操作一律不允许 .catch(() => undefined)。

## 2. saveOnly / submitNow 错误传播（P0-2）

- 抽出 persistObservation()：只负责创建或更新，**失败直接抛出**，不再在内部吞异常。
- submitNow()：先 persistObservation()，异常直接进入 catch → 提示「提交失败，请重试」，**不会**在保存失败的情况下继续提交旧记录。
- saveOnly()：独立 loading（saving），成功提示「草稿已保存」，失败提示「保存失败，请检查网络后重试」。
- 提交前增加 console.debug("submit observation", { id, body }) 便于定位（详细修复方案 §24.1）。

## 3. 学生观察详情改用学生端接口（P0-3）

- 详情页支持两种模式：
  - 本人记录（默认）：GET /api/student/plant/observations/{id} + /photos，**草稿 / 待审核 / 已驳回 / 已通过都能查看**；
  - 公开浏览（?public=1，来自展廊 / 地图 / 精选）：GET /api/public/plant/observations/{id} + 评论。
- 新增后端接口 GET /api/student/plant/observations/{id}/review（返回最近一条审核记录），
  学生能看到**驳回原因**；被驳回时光态提示「修改后可在我的植物观察重新提交」。
- 详情页新增操作区：继续编辑 / 提交审核 / 撤回，并按状态显示对应说明。
- 评论与评分仅对「审核通过」的记录开放，未通过时给出说明文案。

## 4. 图片访问链路（P0-4）

- 后端 /media/plants/** 静态映射已存在（PlantMediaWebConfig），实测 fileUrl 直接 GET 返回 200 image/jpeg；
- Nginx 容器由只读卷提供 /media/plants/**（frontend/nginx.conf），实测 200；
- App 媒体地址统一走 resolveMediaUrl()（getMediaOrigin() 支持运行期自定义后端地址）；
- 教师端审核弹窗修正：缩略图取 thumbnailUrl || fileUrl，**大图预览列表同样经过 resolveMediaUrl()**（修复前 preview-src-list 直接使用原始相对路径）。

## 5. 审核端点健壮性（P0-5）

- 复现：向 /api/teacher/plant/reviews/{id}/approve 传 action=REJECTED，旧实现忽略 action 直接通过（静默误操作风险）。
- 修复：approve / reject 端点均按请求体 action 分派（REJECTED → 驳回、APPROVED → 通过），保持端点语义的同时消除误用风险。
- 实测：/approve + REJECTED → 状态 REJECTED 且学生可见驳回意见；重新提交后 /approve + APPROVED → APPROVED。

## 6. 地图编码统一与台湾省（P1）

- 新增 featureCode(feature)：统一从 properties.adcode（优先）或 feature.id 读取省份编码，
  用于 featuresMap / 标签渲染 / 点击命中判断 / 边界查询 / 省份中心；
- 高亮落到 MapLibre 时使用 GeoJSON 的 feature.id（业务仍用 provinceCode），避免两者混淆；
- 地图加载后校验：若省份编码中缺少 710000，控制台 warn 提示检查 GeoJSON；
- 台湾省几何已在上一轮补齐（34 个省级要素，13 个岛屿环），本轮再次实测：
  GeoJSON via nginx 含 710000；/api/public/plant/map/provinces/710000/works 返回 total=0（空状态）；
  省份点击 / 标签 / 高亮均按 adcode 驱动，无需台湾专用代码。

## 7. 手机端 2D 中国地图（P2）

| 项 | 实现 |
|---|---|
| 渲染方案 | ECharts（core + MapChart + VisualMap + CanvasRenderer，按需引入） |
| 数据 | scripts/build-china-geojson.py 额外产出移动端简化版（2 位小数 + Douglas-Peucker 抽稀，148KB，34 省级含台湾），随 App 打包内置 |
| 交互 | 省份点击 → emit(provinceCode, name)；roam 缩放拖动；hover 提示「N 条观察」 |
| 着色 | visualMap 按观察数量分级（浅绿 → 深绿） |
| 下部面板 | 未选中：Top 8 省份排行（可点击）；选中：名称 + 观察/物种/学生数 + 作品网格（缩略图、植物名、花名·城市）+ 查看更多 + 空状态 |
| 详情联动 | 点击作品 → 详情页公开模式（?public=1） |
| 平台降级 | 非 H5 平台自动回退为省份列表文案（ECharts 依赖 DOM） |

验证：student-app npm run type-check 通过、npm run build:h5 通过；nginx 部署后
/jingxuan/app/ 200、/jingxuan/app/static/geo/china-provinces.json 200（151KB）。

## 8. 本轮同时修复的类型债

App 端此前只跑 build 不做类型检查，本轮开启 type-check 后修复：
MyObservation/ObsDetail 字段补齐（cityCode/districtCode/displayName/reportedCommonName 等）、
局部函数与导入同名遮蔽（searchSpecies）、uni 事件类型（DynamicPlantForm / 详情页 / 编辑页的 $event 处理）、
<button type> 取值限制（统一改用 .btn-primary/.btn-warn 全局样式）、过期 onUnload 残留。

## 9. 验收记录（本地运行栈实测）

| 环节 | 结果 |
|---|---|
| 草稿创建 | 200，状态 DRAFT |
| 图片上传 | 200，fileUrl 可直接 GET 返回 200 |
| 提交审核 | 200，数据库状态 SUBMITTED，submitTime 写入 |
| 未审核时审核意见接口 | 返回空（无记录） |
| 教师驳回（action=REJECTED） | 状态 REJECTED，学生端可见「照片不清晰，请重拍」 |
| 修改后重新提交 + 通过 | 状态 APPROVED |
| 公开详情 / 展廊 / 花名 | 200，显示花名「青禾」 |
| 图片 URL | 200 image/jpeg |
| 清理 | 管理员下线测试记录 200 |

## 10. 后续可做

- 小程序 / 原生打包如需地图，改用 lime-echart 或 renderjs 承载 ECharts；
- 提交前本地校验（照片数量、观察时间必填）以减少一次往返；
- 审核意见在 App 消息页同步展示（当前走通知 + 详情页）。
