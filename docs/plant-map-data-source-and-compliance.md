# 中国地图数据来源与合规说明（plant-map-data-source-and-compliance）

## 边界/几何数据来源
- 来源：阿里云 DataV.GeoAtlas（https://geo.datav.aliyun.com/areas_v3/bound/）行政区 GeoJSON（2026-09 抓取）。
- 用途：仅前端可视化绘制行政边界、计算行政区展示中心与范围（导入 sys_region.center/min/max 字段），以及镜头动画 bounds。
- 本仓库不提交原始大体积 GeoJSON 文件；由 scripts/import-plant-regions.py 按需抓取并缓存于 data/geo/raw/（不入库 git）。

## 行政区代码与名称
- 代码采用 GB/T 2260 省级编码体系；全国 34 个省级、地级市与区县数据按 DataV GeoJSON 的 adcode/name 提取。

## 展示口径（避免误导）
- 系统不获取学生 GPS/设备位置；地图仅按学生主动选择的省/市/区县聚合。
- sys_region.center_lng/center_lat 是行政区域展示锚点（默认取外包框中心），并非植物精确坐标。
- 地图固定说明文案：地图按学生主动选择的行政区域聚合展示，不读取学生设备定位；节点不代表精确采集位置；省份高度表示公开观察数量，不代表地形高度。

## 合规待办（正式公开展示前必须完成）
- [ ] 用自然资源部标准地图服务系统（bzdt.tianditu.gov.cn）核验并替换公开展示用中国地图，标注审图号；
- [ ] 若对边界数据做编辑后公开使用，按现行地图管理规定履行地图审核；
- [ ] 本文档随地图数据版本更新（记录抓取日期与版本）。

## 数据版本记录
- 2026-09-09：V11 可视化字段；首版全国省市县导入（provinces + cities + districts）。
