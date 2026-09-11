# 交付验收清单（Plant Observation Platform v1.0-rc1）

> 面向交付现场：按本清单逐项验收即可。每项都给出了「怎么验」与「期望结果」。
> 所有校验脚本都在 `scripts/` 下，均为真实 HTTP / 真实浏览器执行，不 mock。

## 1. 访问入口与账号

| 入口 | 地址 | 说明 |
|---|---|---|
| 公开端（含 3D 地图 / 展廊） | `http://<服务器IP>/jingxuan/` | 未登录可浏览展廊、中国地图、物种库 |
| 学生端 App（H5 / APK 壳） | `http://<服务器IP>/jingxuan/app/` | 手机浏览器直接打开；APK 壳实时加载同一地址 |
| 教师端 | `http://<服务器IP>/jingxuan/teacher/dashboard` | 侧边栏底部「对外展示」可一键新标签打开展廊 |
| 管理端 | `http://<服务器IP>/jingxuan/admin/...` | 物种库、类别、记录下线/上架 |
| 后端接口 | `http://<服务器IP>/api/` | Spring Boot，健康检查 `/actuator/health` |
| 植物图片 | `http://<服务器IP>/media/plants/...` | Nginx 只读卷直出，30 天缓存 |

演示账号（仅本地试用环境，生产请新建并改密）：`admin` / `tea1` / `stu1` / `stu2`，密码均为 `admin123`。

## 2. 部署（含两个必须确认的点）

```bash
# 1) 学生端 H5 构建并镜像到前端 public 目录（顺序不能反）
cd student-app && npm run build:h5
robocopy dist\build\h5 ..\frontend\public\app /MIR      # Linux: rsync -a --delete

# 2) 构建并启动（后端有 Java 改动时必须一起重建）
cd .. && docker compose up -d --build backend nginx

# 3) 必查：容器是否真的跑在新镜像上（踩过坑，见 §5）
docker exec jingxuan-nginx-1 sh -c "ls /usr/share/nginx/html/jingxuan/app/assets/ | grep map-index"
Get-ChildItem student-app/dist/build/h5/assets -Name | Select-String map-index   # 两者哈希必须一致
```

## 3. 验收清单

### 3.1 一条命令跑完的自动化验收

```bash
python scripts/check-china-geojson.py            # 地图数据：34 省级要素、含台湾、PC/App 同源
python scripts/check-photo-access.py             # 图片链路：上传 → /media/plants 直连 200 且为图片
python scripts/check-plant-notifications.py      # 通知闭环：驳回/通过/点评/精选/已读
python scripts/check-mobile-map-and-features.py  # 手机端地图可见性（像素级）+ 兜底 + 部署新鲜度
python scripts/check-app-ui-redesign.py          # 学生端改版、底部图标、地图入口跳转、教师端入口
python scripts/verify-ui-playwright.py           # PC 地图台湾省、手机地图点击、展廊图片
python scripts/plant-e2e-loop.py                 # 主闭环 E2E
python scripts/plant-v4-smoke.py                 # V4 能力冒烟
```

### 3.2 人工验收要点

| 角色 | 步骤 | 期望 |
|---|---|---|
| 学生（手机） | 打开 `/jingxuan/app/` → 登录 stu1 → 首页「全国地图」 | 进入地图页并**看得到中国地图**；手指在地图上滑动不会把地图拖走；点省份弹出该省观察 |
| 学生（手机） | 底部「采集」→ 拍照 → 选地点 → 保存草稿 | 草稿进入「我的植物」，卡片带封面缩略图 |
| 学生（手机） | 「我的植物」→ 提交审核 | 状态变为待审核，卡片不再提示"无照片" |
| 教师（PC） | 教师端 → 植物观察审核 → 驳回并填意见 | 学生端「消息」出现"观察被驳回"及意见 |
| 教师（PC） | 通过审核 → 教师点评 | 学生端「消息」出现"观察已通过审核""收到教师点评" |
| 教师（PC） | 侧边栏底部「对外展示 → 植物展廊」 | 新标签页打开展廊，教师端页面不跳走 |
| 公开端 | `/jingxuan/plant/map` | 3D 地图**看得到台湾省**（含名称标签），点击弹出省份面板 |
| 公开端 | 展廊 | 图片正常显示（非破图、非占位） |

## 4. 本轮修复的真因（交付重点，避免复现）

| 现象 | 真因 | 修复 |
|---|---|---|
| PC 地图看不到台湾省 | ① `fill-color` 多标签写法非法 → MapLibre 丢弃整个 fill 图层；② "台湾省"标签盖住 14×18px 的岛；③ 边界 JSON 被 nginx 标成 `immutable` 一年，而台湾是**后补**进文件的（33 省 → 34 省），老浏览器一直用旧副本 | ① 改 `step`；② 小省标签偏移；③ 内容指纹 `?v=` + `/geo/*.json` 改协商缓存 |
| 手机端已上传图片不显示 | uni-app H5 的 `<image>` 把 `/media/plants/...` 按**应用根**解析成 `/jingxuan/app/media/plants/...`，nginx 回退 SPA 返回 `200 text/html` | `resolveMediaUrl` 一律输出**绝对地址** |
| 手机端地图点不动 | `uni.switchTab` 对**非 tabBar 页面**（`pages/map/index`）静默失败 | 改 `uni.navigateTo` |
| 手机端地图看不到 | ① **ECharts 与 Vue 争抢容器子节点**：图表 DOM 被插进由 Vue 管理的容器，页面切换时抛 `Cannot read properties of null (reading 'insertBefore')`，留下半截/空白图表（直接打开地图 URL 测不出来，只有"首页→点地图"这条真实路径才触发）；② `roam:true` 让手指滑动把地图拖出可视区；③ 容器尺寸为 0 时 ECharts 画出空图且无提示 | ① 图表容器 `.china-map__canvas` 内**不渲染任何 Vue 节点**，遮罩/提示放到兄弟节点；② `roam:false`；③ 等尺寸再初始化 + 失败/超时切**省份列表兜底** |
| 消息通知永远为空 | 通知只在**旧作品域**（work/audit/publish）发送，植物模块从未发通知 | 审核通过/驳回、教师点评、推荐优秀均发通知 |

## 5. 常见问题排查（现场最可能遇到）

1. **"我这边还是旧界面"** → 先确认线上产物哈希与本地一致（§2 第 3 步）。
   本项目踩过两次：nginx 容器没重建、镜像层缓存，都会导致"代码已提交但线上没变"。
   兜底手段：`docker compose build --no-cache nginx && docker compose up -d --force-recreate nginx`。
2. **地图空白** → 页面会显示「地图暂不可用，已切换为省份列表」并给出「重试地图」。
   若出现该提示，检查 `/jingxuan/app/static/geo/china-provinces.json` 是否 200。
3. **图片不显示** → 检查图片 URL 是否为**绝对地址**（形如 `http://IP/media/plants/...`），
   出现 `/jingxuan/app/media/plants/...` 即说明走了错误的应用根解析。
4. **浏览器缓存** → 地图边界与静态资源已带内容指纹，普通刷新即可；不需要清缓存。
5. **Docker 起不来** → 先 `docker ps -a --filter name=jingxuan` 清理同名残留容器再 `up -d`。
6. **接口"调用成功"但没生效** → 本项目统一返回 `Result<T>`，**HTTP 200 也可能是业务失败**（`code=400`）。
   例如"该记录不在待审核状态"会让审核静默不生效、通知自然也不会产生。
   排查时务必看响应体的 `code`，不要只看 HTTP 状态码（`check-plant-notifications.py` 已内置该校验）。

## 6. 已知限制（交付时需说明）

- **真机未回归**：Capacitor 壳实时加载同一 H5 地址，行为与 H5 一致；若改用 uni-app 原生打包，地图走 renderjs 分支，建议真机回归一次。
- **台湾省暂无真实观察数据**：地图与展廊均能正常展示台湾省，但该省没有学生上传的记录（点击显示空状态）。
- **地图边界为简化几何**：台湾为 13 环 MultiPolygon（PC 169 点 / App 85 点），未含全部离岛细节。
- **通知依赖业务事件**：只有审核、点评、推荐优秀等动作会触发；系统不会主动推送。
- **旧作品域保留**：work/audit/score/rank/prize 代码与页面停用未删除，与植物平台并存（见 `docs/legacy-work-retirement.md`）。
- **APK 分发**：安装包通过 GitHub Release 分发，需 `gh auth refresh -h github.com -s workflow` 权限。

## 7. 本轮新增校验脚本

| 脚本 | 覆盖 |
|---|---|
| `check-mobile-map-and-features.py` | 地图像素级可见性、手势拖拽后仍可见、加载失败兜底、部署新鲜度、我的植物封面 |
| `check-plant-notifications.py` | 驳回/通过/点评/精选通知、标记已读、全部已读 |
| `check-app-ui-redesign.py` | 底部 5 图标互异、首页视觉、地图入口跳转、教师端展廊入口 |
| `check-app-layout.py` / `check-app-bottom-safe-area.py` | 无横向溢出、底部导航固定、内容不被遮挡 |
| `check-media-and-geo-urls.py` | 图片绝对地址、地图边界版本指纹、旧缓存污染回归 |
| `check-taiwan-visibility*.py` | 台湾省区域像素采样 |
