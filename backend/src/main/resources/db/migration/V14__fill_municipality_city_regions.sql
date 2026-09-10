-- V14: 补齐直辖市「市辖区」CITY 级行政区记录，并回填观察记录中缺失的城市名称。
-- 背景：北京市(110000)/天津市(120000)/上海市(310000)/重庆市(500000) 的 CITY 级代码（xx0100）
--       在既有种子数据中缺失，导致学生选择直辖市时 city_name 为空、地图卡片缺少城市信息。
-- 只增不改：新增 4 行 + 回填空值，不修改既有行政区与业务数据。

INSERT INTO sys_region (id, region_code, region_name, parent_code, region_level, sort_order, enabled, create_time, update_time, deleted)
SELECT 990000000000000101, '110100', '北京市', '110000', 'CITY', 1, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_region WHERE region_code = '110100');

INSERT INTO sys_region (id, region_code, region_name, parent_code, region_level, sort_order, enabled, create_time, update_time, deleted)
SELECT 990000000000000102, '120100', '天津市', '120000', 'CITY', 1, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_region WHERE region_code = '120100');

INSERT INTO sys_region (id, region_code, region_name, parent_code, region_level, sort_order, enabled, create_time, update_time, deleted)
SELECT 990000000000000103, '310100', '上海市', '310000', 'CITY', 1, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_region WHERE region_code = '310100');

INSERT INTO sys_region (id, region_code, region_name, parent_code, region_level, sort_order, enabled, create_time, update_time, deleted)
SELECT 990000000000000104, '500100', '重庆市', '500000', 'CITY', 1, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_region WHERE region_code = '500100');

UPDATE plant_observation
SET city_name = (SELECT r.region_name FROM sys_region r WHERE r.region_code = plant_observation.city_code AND r.deleted = 0)
WHERE deleted = 0
  AND city_code IS NOT NULL
  AND (city_name IS NULL OR city_name = '')
  AND EXISTS (SELECT 1 FROM sys_region r WHERE r.region_code = plant_observation.city_code AND r.deleted = 0);
