# 全国 3D 植物地图：交互链路实现说明（P0/P1/P2）

> 依据《全国 3D 植物观察地图_下一步开发计划》实现。范围：省份名称常驻显示、province_code/adcode 编码统一、
> 点击省份加载学生作品与侧边抽屉、全国精选作品卡片与 SVG 引导线、学生公开展示花名、hover 联动与图片性能优化。

## 1. 验收对照（计划 → 实现）

| 计划条目 | 实现 | 位置 |
|---|---|---|
| §3.1 省份名称常驻显示 | 省份标签层（DOM 文本标签，随缩放显隐，低缩放隐藏小面积省份），描边保证 3D 背景可读 | frontend/src/modules/plant-map/composables/usePlantMap.ts（renderProvinceLabels） |
| §3.2 统一 province_code / adcode | 全链路使用 GeoJSON properties.adcode（6 位省级编码）：点击取码 → 高亮 feature-state → 查询接口 provinceCode → 数据库 province_code | usePlantMap.featureAt / ChinaPlantMap.handleProvinceClick |
| §3.3 点击省份加载学生作品 | 点击即请求 /api/public/plant/map/provinces/{code}/works（首屏 12 条，缩略图/植物名/花名/城市/描述/精选/时间） | ChinaPlantMap.loadProvinceWorks |
| §3.4 省份作品侧边抽屉 | 右侧面板：省名 + 统计（观察/物种/学生）+ 精选优先 + 全部作品网格 + 查看更多 + 空状态 + loading；点击作品进入既有详情页 | components/ProvinceWorkDrawer.vue、PlantWorkCard.vue |
| §4.1 全国精选作品 | /api/public/plant/map/featured-works（默认 8 条，精选优先，同一省份最多 1 条，不足用最新公开记录补齐） | backend PlantMapService.featuredWorks |
| §4.2 精选作品卡片 | 地图外围卡片（缩略图 + 植物名 + 省份 + 花名·城市），hover 放大并高亮对应省份与引导线 | components/FeaturedWorkOverlay.vue |
| §4.3 精选作品与省份引导线 | 省份中心（bbox 中心）→ map.project() 屏幕坐标 → SVG 折线（水平折点 + 垂直折点）连接卡片侧边；地图移动/缩放自动重算 | utils/mapLayout.ts（connectorPath）、ChinaPlantMap.onCameraChange |
| §4.4 卡片自动布局 | 西侧省份放左、东侧放右；同侧按锚点 Y 排序；保证最小间距；超出画布自下而上回压 | utils/mapLayout.ts（layoutFeaturedCards） |
| §5.1 学生公开展示花名 | V13 迁移 sys_user.display_name；公开端 displayName 优先、未设置按隐私策略回退；学生可自助设置 | PlantPrivacy.publicName、PlantProfileService、PUT /api/student/plant/profile/display-name |
| §6.1 省份 Hover | 悬停省份高亮 + 悬浮提示（观察数/物种数） | usePlantMap.hoverFeature、ChinaPlantMap 模板 |
| §6.2 双向联动 | 卡片 hover → 省份高亮 + 连线高亮；省份 hover → 对应卡片高亮（highlightCode → effectiveActive） | ChinaPlantMap.onCardHover、FeaturedWorkOverlay |
| §6.3 点击省份镜头聚焦 | 点击后 fitBounds 到该省（保留 reduced-motion 适配），右侧抽屉同时打开 | handleProvinceClick、usePlantMap.flyToBounds |
| §6.4 图片性能优化 | 卡片统一使用缩略图（thumbnailUrl）+ lazy loading + 失败回退占位图 | PlantWorkCard（loading=lazy、onMediaError） |

## 2. 交互链路

    打开全国地图
      → 显示中国 3D 地图 + 省份名称标签
      → 加载全国精选作品（≤8 条，每省最多 1 条）并投影为屏幕锚点
      → SVG 折线把每张卡片连到对应省份，hover 卡片高亮省份与连线
      → 点击四川（adcode 510000）
      → 省份高亮并聚焦，右侧打开「学生作品」抽屉（精选优先 + 网格）
      → 可切换到「城市与物种」查看省内城市分布与物种 TOP
      → 点击任一作品 → /plant/observations/{id} 详情页

## 3. 接口

| 接口 | 说明 |
|---|---|
| GET /api/public/plant/map/china | 省级聚合统计（观察数/物种数/学生数），地图着色与悬浮提示 |
| GET /api/public/plant/map/featured-works?size=8 | 全国精选作品，每省最多 1 条，含 displayName 花名 |
| GET /api/public/plant/map/provinces/{provinceCode}/works?page=1&size=12 | 某省公开作品分页（点击省份后加载） |
| GET /api/public/plant/map/provinces/{provinceCode}/visualization | 省内城市/区县聚合与可视化字段（城市与物种页签） |
| GET /api/public/plant/map/species/{speciesId}/observations | 某物种观察记录（物种抽屉） |
| PUT /api/student/plant/profile/display-name | 学生设置公开展示花名（2-16 字符，不含空格） |
| GET /api/student/plant/profile/display-name | 查看自己的花名 |

公开接口只返回 status=APPROVED 且 is_public=1 的记录；管理员下线（OFFLINE）后立即从地图与列表中消失。

## 4. 前端组件拆分

    ChinaPlantMap.vue                 页面编排（状态机、抽屉切换、精选数据、URL 同步）
    ├── MapFilterBar.vue              筛选（类别/班级/年份/关键词）
    ├── FeaturedWorkOverlay.vue       全国精选卡片 + SVG 引导线 + hover 联动
    │   └── PlantWorkCard.vue         通用作品卡片（缩略图/花名/城市/精选角标）
    ├── ProvinceWorkDrawer.vue        省份作品抽屉（精选优先 + 网格 + 查看更多）
    ├── ProvincePlantPanel.vue        省内城市与物种可视化（原能力保留）
    └── SpeciesObservationDrawer.vue  物种维度作品列表
    composables/usePlantMap.ts        MapLibre 3D 地图（图层、标签、投影、feature-state、相机）
    utils/mapLayout.ts                卡片自动布局与折线引导线（纯函数，含单测）

## 5. 关键实现决策

- **省名用 DOM 标签而非 symbol 图层**：MapLibre symbol 图层需要 glyphs 字体服务（PBF 字体按字符区间请求），离线/校园内网部署会缺字或整层不显示；DOM 标签零依赖、中文渲染稳定，且天然支持键盘聚焦与点击/hover。代价是标签数量等于省份数（33 个），性能可忽略。缩放小于 3.4 时隐藏北京/天津/上海/香港/澳门等小面积标签，避免文字互相遮挡（对应计划中"小区域后续单独调整"）。
- **编码统一**：地图数据 properties.adcode 与数据库 plant_observation.province_code 均为 6 位省级编码，前端点击取码后直接用于查询与高亮，不再做中文名称匹配。
- **引导线随相机重算**：通过 usePlantMap.onCameraChange 订阅 move/zoom，用 map.project() 重新投影省份中心点后重算布局与折线；退出全国视图时暂停计算。
- **花名与隐私**：优先展示 display_name；未设置时按 plant.privacy.show-real-name 决定显示真名或掩码（张*、欧**娜）。登录与后台管理始终使用真实身份。
- **图片**：全国精选与省份列表一律使用缩略图并懒加载，加载失败回退占位图，避免大量原图拖慢地图。

## 6. 演示数据与花名设置

    # 灌入真实植物照片（Wikimedia Commons，含署名）并走完整审核链路
    python -X utf8 scripts/seed-demo-photos.py --base http://127.0.0.1:8080

    # 设置学生花名（示例）
    curl -X PUT http://127.0.0.1:8080/api/student/plant/profile/display-name \
         -H "Authorization: Bearer <student-token>" -H "Content-Type: application/json" \
         -d '{"displayName":"青禾"}'

署名与许可清单见 docs/demo-photo-credits.md。

## 7. 验证

| 项 | 结果 |
|---|---|
| 后端单元测试 | 436 / 436（含 PlantPrivacyTest、PlantProfileServiceTest、PlantMapServiceTest 12 例） |
| 前端 typecheck / lint | 通过 |
| 前端单元测试 | 101 通过（新增 mapLayout 布局与引导线 5 例、FeaturedWorkOverlay 渲染与联动 4 例） |
| 前端生产构建 | 通过 |
| 全栈 E2E 回归 | 37 项全部通过（主闭环未受影响） |
| V4 冒烟回归 | 25 项全部通过（含自动清理测试记录） |
| 运行栈接口实测 | 见 §8 记录 |

## 8. 实测记录（本地运行栈，2026-09-10）

    # 花名
    PUT /api/student/plant/profile/display-name {"displayName":"青禾"}  -> code 200
    GET /api/student/plant/profile/display-name                          -> "青禾"

    # 全国精选（每省最多 1 条；信息完整优先，待鉴定记录让位于有物种名的记录）
    GET /api/public/plant/map/featured-works?size=8 -> 5 条
      320000 爬山虎  江苏省 南京市 青禾
      110000 狗尾草  北京市 北京市 青禾
      410000 牡丹    河南省 洛阳市 青禾
      510000 珙桐    四川省 成都市 青禾
      330000 银杏    浙江省 杭州市 青禾

    # 省份作品
    GET /api/public/plant/map/provinces/330000/works -> total 2（浙江省）
    GET /api/public/plant/map/provinces/110000/works -> total 1（北京市，cityName=北京市）
    GET /api/public/plant/map/provinces/999999/works -> total 0（空状态）

    # 迁移与页面
    flyway_schema_history: V13 add user display name / V14 fill municipality city regions -> success
    http://127.0.0.1/jingxuan/plant/map -> 200；/jingxuan/geo/china-provinces.json -> 200

顺带修复：4 个直辖市（北京/天津/上海/重庆）的 CITY 级行政区在既有种子数据中缺失，导致城市名解析为空，
V14 迁移补齐 xx0100 记录并回填历史观察记录的 city_name（只增不改）。

## 9. 性能与移动端适配（计划 §6.4、第三阶段）

本地运行栈（Windows + Docker，单实例，限流阈值以内速率）实测：

| 接口 | n | p50 | p95 | max |
|---|---|---|---|---|
| /map/featured-works | 30 | 19.9ms | 28.8ms | 84.8ms |
| /map/provinces/{code}/works | 30 | 18.9ms | 28.8ms | 30.8ms |
| /map/china | 30 | 16.5ms | 27.4ms | 32.7ms |
| /map/provinces/{code}/visualization | 30 | 15.7ms | 22.3ms | 25.0ms |

- 以 8 并发 40 次请求压测时会返回 HTTP 429：公开接口限流（20 次/秒/IP）按预期生效，属于保护而非缺陷；正式压测请使用内网直连或提高限流配置。
- 图片：精选与省份列表一律使用 320px 缩略图（thumbnailUrl）+ lazy loading + 失败占位；详情页才加载原图。
- 移动端：地图宽度小于 900px（手机/小窗）时隐藏精选卡片与引导线，避免遮挡地图主体；右侧抽屉在小屏改为底部面板（max-height 52vh）；省份名称标签在小屏降为 10px。

## 10. 后续可做

- 注册流程自动生成花名（避免为空）与花名唯一性校验、敏感词过滤
- 评论、评分处也展示花名（当前评论仍按隐私策略展示姓名）
- 精选作品后台管理入口（管理员/教师挑选入图）
- 移动端手势与低端设备性能压测（当前已做标签显隐与降级）
