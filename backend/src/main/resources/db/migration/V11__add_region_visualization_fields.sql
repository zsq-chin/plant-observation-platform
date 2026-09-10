-- ============================================================
-- V11: sys_region 行政区可视化字段（地图展示锚点）
-- 说明: 字段表示行政区域展示中心/范围（用于 3D 地图镜头与节点锚点），
--       不是学生位置；系统不采集学生 GPS。
-- ============================================================
ALTER TABLE sys_region
    ADD COLUMN center_lng DECIMAL(10,6) DEFAULT NULL COMMENT '展示中心经度（行政区锚点，非学生位置）',
    ADD COLUMN center_lat DECIMAL(10,6) DEFAULT NULL COMMENT '展示中心纬度（行政区锚点，非学生位置）',
    ADD COLUMN min_lng DECIMAL(10,6) DEFAULT NULL COMMENT '范围最小经度',
    ADD COLUMN min_lat DECIMAL(10,6) DEFAULT NULL COMMENT '范围最小纬度',
    ADD COLUMN max_lng DECIMAL(10,6) DEFAULT NULL COMMENT '范围最大经度',
    ADD COLUMN max_lat DECIMAL(10,6) DEFAULT NULL COMMENT '范围最大纬度';
