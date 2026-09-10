-- ============================================================
-- V6: 学生注册班级字典种子数据（1班 / 2班 / 3班）
-- 说明: 学生注册必须选择班级（后端强制校验 classId，前端注册页
--       Register.vue 通过 /api/v1/classes 读取下拉选项），该接口
--       返回 sys_dict 中 dict_type='class' 的行。此前 V1–V5 均未
--       写入任何班级字典数据，全新数据库会导致注册页无班级可选。
--       本迁移补齐 1班/2班/3班 三个默认选项。
--
-- 约定:
--   * dict_label 为用户可见的班级名称（注册下拉框显示值）。
--   * dict_value 为评分批次 classScopes 的匹配键（如 ["1","2"]），
--     需与学生班级字典行的 dict_value 一致。
--   * id 采用 5/6/7：避开测试夹具已占用的 1–4（class）与 10–12
--     （tech_stack），避免与既有部署数据冲突。
--   * sort 取 5/6/7，保证排在既有班级（sort 1–4）之后，
--     同时保持 1班 → 2班 → 3班 的顺序稳定。
-- ============================================================

INSERT INTO sys_dict (id, dict_type, dict_label, dict_value, sort, remark)
SELECT 5, 'class', '1班', '1', 5, '注册阶段默认班级'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'class' AND dict_label = '1班' AND deleted = 0);

INSERT INTO sys_dict (id, dict_type, dict_label, dict_value, sort, remark)
SELECT 6, 'class', '2班', '2', 6, '注册阶段默认班级'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'class' AND dict_label = '2班' AND deleted = 0);

INSERT INTO sys_dict (id, dict_type, dict_label, dict_value, sort, remark)
SELECT 7, 'class', '3班', '3', 7, '注册阶段默认班级'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'class' AND dict_label = '3班' AND deleted = 0);
