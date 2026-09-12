# 学生端 App 视觉系统与入口说明

> 起因：手机端页面偏"单调"（白底 + 直角小卡片），底部 5 个 Tab 还共用同一个 logo 图标；
> 同时首页点「全国植物地图」没有任何反应。本文件记录改版的视觉规范与入口修复。

## 1. 「中国地图」点不动：switchTab 对非 tabBar 页面静默失败

`pages/map/index` **不在 tabBar 里**（tabBar 只有 首页 / 展廊 / 采集 / 我的植物 / 我的），
但首页入口用了 `uni.switchTab`：

    // 修复前：map 不是 tabBar 页面 → 调用被忽略，页面毫无反应，也不报错
    uni.switchTab({ url: name === "gallery" ? "/pages/gallery/index" : "/pages/map/index" })

    // 修复后
    function goMap() { uni.navigateTo({ url: "/pages/map/index" }) }        // 非 tabBar 页面
    function goGallery() { uni.switchTab({ url: "/pages/gallery/index" }) } // tabBar 页面

**记法**：目标页在 `pages.json` 的 `tabBar.list` 里才用 `switchTab`，其余一律 `navigateTo`。
实测：点击首页「全国地图」→ URL 变为 `#/pages/map/index`，ECharts 画布 353×330 正常渲染。

## 2. 视觉系统（统一在 App.vue 全局样式）

| 项目 | 取值 |
|---|---|
| 页面底色 | `#f5f8f2`（淡绿白，替代纯白） |
| 品牌渐变 | `135deg, #4aa64a → #2f7a34 → #276b2e` |
| 卡片 | 白底 / 圆角 `24rpx` / 阴影 `0 8rpx 24rpx rgba(31,45,36,.06)` |
| 文字 | 主 `#1f2d24`、次 `#55645a`、弱 `#7b8a80` |
| 状态色 | 待完善灰 / 待审核蓝 / 已通过绿 / 被驳回红 / 优秀琥珀 |
| 圆角按钮 | `999rpx` 胶囊 + 品牌渐变 + 阴影 |

通用类（任何页面直接用）：`.page` `.card` `.chip` `.btn-primary` `.btn-ghost` `.ipt`
`.sec` `.stat` `.tile` `.item` `.hero` `.lift` `.empty-state`。

复用组件：

- `components/AppHero.vue`：渐变头图（带 `--status-bar-height` 安全区），首页/展廊/我的植物/我的 共用；
- `components/SectionTitle.vue`：带品牌色竖条的区块标题；
- `components/EmptyState.vue`：图标 + 标题 + 提示的空状态（替代原来的灰字）；
- `components/ObservationCard.vue`：展廊/首页卡片重做（220rpx 封面、地名 chip、互动数据行）。

## 2.1 顶部布局原则（第二轮调整）

反馈："顶部有一块遮挡住其他内容的块，缩小；快捷入口放上面"。已按此调整：

- **头图压扁**：`padding` 由 `34rpx/76rpx` 收到 `22rpx/34rpx`，标题 46→40rpx，底部圆角 40→28rpx；
  实测首页头图 **75px**（原约 200px，因为原来还塞了一行"采集植物/全国地图"大按钮，已移除）
- **取消负边距重叠**：`.lift` 由 `margin-top:-56rpx` 改为 `0`，我的植物页状态胶囊也不再压住头图。
  之前卡片与头图互相压着，看起来就是"顶上一块盖住内容"
- **快捷入口置顶**：首页内容区第一块就是 6 宫格（采集植物 / 全国地图 / 植物展廊 / 我的植物 / 消息通知 / 我的），
  其次是本地草稿提醒、统计块、最新优秀观察
- **地图页顶部信息块压扁**：`padding` 与字号收紧，实测 **108px**（原约 170px），地图不再被压

对应校验脚本 `scripts/check-app-top-layout.py`：头图高度、内容与头图零重叠、快捷入口为内容区首块且在统计块之上、
地图页顶部块高度与地图未被遮挡、其余页面同样零重叠。

## 3. 底部导航：5 个图标各不相同

原来 5 个 Tab 都指向 `static/logo.png`，看起来像没做完。现改为
`static/tabbar/{home,gallery,capture,plants,profile}{,-on}.svg` 共 10 个矢量图标
（未选中 `#9aa79c`、选中 `#3f9b3f`），由 `pages.json` 的 `iconPath/selectedIconPath` 引用。

## 4. 教师端：新增「对外展示」入口

`TeacherLayout.vue` 侧边栏新增「植物展廊」与「全国植物地图」两个入口，
用 `router.resolve({ name }).href` 生成**真实链接**并以 `target="_blank"` 新开标签页：
教师核对"学生看到的展廊"时不会丢掉评审现场（`el-menu` 的 `router` 模式是 SPA 内跳转，会离开教师工作区）。

## 5. 自动化校验

- `scripts/check-app-ui-redesign.py`：底部 5 图标互不相同且加载成功、首页渐变头图/4 统计块/6 快捷入口、
  **点击「全国地图」跳转成功且画布有尺寸**、各页面有内容渲染、无控制台错误、教师端入口存在且新标签打开；
- `scripts/check-app-layout.py`：无横向溢出、底部导航固定在视口内、教师端入口可见；
- `scripts/check-app-bottom-safe-area.py`：滚到底部时最后一个元素不被底部导航遮挡。
