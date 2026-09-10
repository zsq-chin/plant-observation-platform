# 台湾省显示 · 手机地图点击 · 图片显示：实测根因与修复记录

> 依据《植物观察平台_台湾省_手机地图_图片显示_详细修改与测试方案》执行。
> 本轮特点：**先用真实浏览器（Playwright）复现并定位根因**，再做最小修复；未画假几何、未只改条件编译、未使用 Base64、URL 统一走 resolveMediaUrl、未改审核核心逻辑。

## 1. PC 3D 地图台湾省：真因是图层样式非法被丢弃

### 复现与定位（Playwright + 页面内 MapLibre 诊断）

    layers            = ["background", "province-extrusion", "province-border"]   ← 缺少 province-fill
    queryRenderedFeatures(province-fill) = 0
    queryRenderedFeatures(province-extrusion) = 37（省份柱体其实都渲染了）

GeoJSON 侧完全正常（34 个省级要素、台湾 710000、MultiPolygon 13 环 169 点、经纬度范围正确），
所以问题不在数据，而在**渲染层**：

- 原实现把「分级着色」的多标签写法放进了 `case` 表达式：

      "fill-color": ["case", cond1, c1, cond2, c2, cond3, c3,
                     ["number", ["get","observationCount"], 0], 0, "#d8e7cc",
                     3, "#bfd9a6", 10, "#9cc47e", ...]

  `case` 只接受「条件, 结果」成对参数 + 末位兜底，多标签是 `match`/`step` 的能力。
  参数错位后颜色位拿到数字 0，**样式校验失败 → MapLibre 直接丢弃整个 fill 图层**
  （不抛异常，只触发内部 error 事件），于是只剩 3D 柱体和白色边界，
  面积最小的台湾岛几乎看不出来 —— 这正是「台湾省不显示」的真相。

### 修复

- `fill-color` 改为 `["step", ["number", ["get","observationCount"], 0], "#d8e7cc", 3, …, 100, "#4f8a44"]`，
  外层仍用 `case` 处理 featured/hovered/selected 高亮；`fill-opacity` 提到 0.9；
- 省界 `line-width` 0.6 → 0.9，小面积省份（台湾、港澳）更易辨认；
- 地图加载后若省份编码缺少 710000 会 console.warn 提示数据问题。

### 实测结果（Playwright）

    layers 含 province-fill；台湾要素在 fill 层渲染（renderedCount 0 → 37，含 710000）
    "台湾省" 标签存在（34 个标签）
    点击台湾 → 面板显示「台湾省 · 0 条观察 · 0 种植物 · 该省暂无公开作品」（provinceCode=710000）

## 2. 手机端地图点击无反应：五个原因叠加

### 逐层定位

| # | 现象 | 根因 |
|---|---|---|
| 1 | 组件直接降级为「省份列表」 | 逻辑层用 **动态 import("echarts/core")**，uni-app 生产构建产出无法解析的模块说明符：`Failed to resolve module specifier '..-node_modules-echarts-core.xxx.js'` |
| 2 | 改用静态导入后仍无 canvas | **uni-app H5 的 `<view>` 不透传 `id`**：`document.getElementById("china-map-canvas")` 恒为 null（页面内所有 id 只有 `app`） |
| 3 | 手动创建承载 div 后 canvas 出现但高度 0 | ECharts 落在 **组件根节点**（无高度）上；uni 组件节点由 Vue 管理，直接交给 ECharts 会被重渲染破坏 |
| 4 | 容器尺寸正确后点击仍无反应 | **renderjs 的 `this.$el` 就是组件根元素**，所以承载地图的根节点必须自带高度（已确认 canvas 390×330） |
| 5 | ECharts 点击日志正常但父页面无反应 | renderjs 中**没有 `uni` 对象**，且 `$ownerInstance.callMethod` 在 H5 中不生效 → 回传链路断开 |

ECharts 点击本身一直是正常的（日志可见命中 四川省 510000 / 澳门 820000 / 内蒙古 150000 等）。

### 修复（统一走 renderjs，H5 与原生 App 通用）

- 组件模板：单一根节点承载地图，**高度、prop 绑定、renderjs 绑定都放在根节点**；
- 渲染逻辑全部移入 `china-map.renderjs.js`（`lang="renderjs"` + `src` 引用），使用 `this.$el` 作为容器，
  ECharts 使用静态 import，GeoJSON 用相对路径 `static/geo/china-provinces.json`（H5 子路径与 App 本地都可用）；
- 点击回传**双通道**：原生 App 用 `this.$ownerInstance.callMethod("onProvinceSelect", …)`；
  H5 用 `window.dispatchEvent(new CustomEvent("china-map-select", { detail }))`，逻辑层监听该事件；
  逻辑层对 600ms 内相同省份做去重，避免双通道重复触发；
- 逻辑层不再操作任何 DOM（彻底避开 uni 组件节点与 Vue 重渲染的冲突）。

### 实测结果

    手机视口 412×915：canvas 390×330 渲染成功，未落入降级分支
    点击四川 → 面板展开「四川省 · 1 条观察 · 1 种植物 · 1 名学生 / 珙桐 青禾 · 成都市」

## 3. 图片显示链路：逐层验证全部通过

| 层级 | 验证方式 | 结果 |
|---|---|---|
| 数据库 | 上传接口返回 fileUrl / thumbnailUrl，遵循 /media/plants 约定 | 通过 |
| 磁盘 | 后端本地存储 original/thumbnail 落盘 | 通过 |
| Spring 静态映射 | `GET /media/plants/**` 直连后端 | 200 且 Content-Type 为图片 |
| Nginx | 容器只读卷提供 /media/plants/** | 200 |
| 手机端渲染 | Playwright 打开公开植物展廊，统计卡片图片 naturalWidth | 6/6 全部加载成功，0 张 broken |
| 教师端 | 审核弹窗缩略图 + 大图预览列表均经 resolveMediaUrl | 已统一（上轮修复，本轮回归） |

结论：本地存储 + fileUrl 体系健康，不需要 Base64 兜底；App 端媒体地址由 `resolveMediaUrl` + `getMediaOrigin()` 统一处理，
可在登录页「服务器设置」中切换后端地址（真机不使用 127.0.0.1）。

## 4. 本轮新增的自动化检查

| 脚本 | 作用 |
|---|---|
| scripts/check-china-geojson.py | 校验 PC 与 App 两份地图：省级数量、编码唯一、必需省份（含 710000/810000/820000）、台湾要素名称与 adcode、几何类型与坐标点数量、两份省份集合一致 |
| scripts/check-photo-access.py | 登录 → 建草稿 → 上传图片 → 断言 fileUrl/thumbnailUrl 均为 /media/plants 前缀、HTTP 200、Content-Type 为图片、内容非空（结束自动清理草稿） |
| scripts/verify-ui-playwright.py | 真实浏览器验证：PC 地图台湾渲染/标签/点击 710000/面板、手机地图 canvas 与点击省份加载作品、展廊图片全部加载 |
| scripts/diagnose-map-ui.py / diagnose-mobile-map.py / diagnose-map-click.py | 定位过程使用的诊断脚本（DOM 结构、图层、canvas 尺寸、点击日志） |

实测输出：`check-china-geojson` 31 项全过、`check-photo-access` 11 项全过、`verify-ui-playwright` 全部通过。

## 5. 与方案的对照

| 方案要求 | 落实情况 |
|---|---|
| §4/§5 先验证 GeoJSON 是否真有台湾几何 | 已用脚本核验（34 省 / 710000 / MultiPolygon / 169 点），并固化为 check-china-geojson.py |
| §6/§8 统一 featureCode、高亮用 feature.id | 已实现（featureCode 优先 properties.adcode） |
| §9 台湾 Label 调试（碰撞） | 现状：DOM 标签层共 34 个，台湾标签正常显示，无需放开碰撞 |
| §12/§13 手机地图用 renderjs + ECharts，不要只改 #ifdef | 已按此实现，H5 与 App 共用 renderjs 渲染与双通道回传 |
| §11/§18 真机（H5 与 App）验证 | H5 已用真实浏览器（Chromium 移动视口）实测通过；Capacitor 壳加载同一 H5 页面，行为一致；uni-app 原生打包走 renderjs 分支（代码结构与官方示例一致，需真机回归） |
| §16 手机端不依赖中文名匹配编码 | renderjs 中建立 省份名 → adcode 映射，点击输出 code |
| §17 PC 与手机同源 GeoJSON | 由同一脚本 scripts/build-china-geojson.py 生成（PC 完整版 + App 简化版 148KB），并有集合一致性校验 |
| §28/§29 统一 resolveMediaUrl、真机不用 127.0.0.1 | 已统一；真机地址通过运行期「服务器设置」注入 |
| §34 禁止事项 | 未画假轮廓、未只改条件编译、未 Base64、未分散拼 URL、未改审核核心 |
| §35 建议增加自动化测试 | 已新增 §4 中的脚本 |

## 6. 仍需真机确认的部分

- Capacitor 壳（当前 APK）加载的就是本 H5，逻辑与实测一致；如需**离线内置模式**需按 `capacitor.config.ts` 注释切换；
- 若后续用 HBuilderX 打包 uni-app 原生 App，地图走 renderjs 分支，建议真机回归一次（点击省份 → 抽屉加载作品）。
