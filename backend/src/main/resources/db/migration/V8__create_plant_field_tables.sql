-- ============================================================
-- V8: 教师动态植物描述项（plant_field_definition / plant_field_value）
-- 说明: 教师可配置“叶形/叶缘/生境”等描述方向，学生端动态渲染，
--       避免为每个新描述方向频繁 ALTER TABLE。
-- ============================================================

CREATE TABLE IF NOT EXISTS plant_field_definition (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    field_code VARCHAR(64) NOT NULL COMMENT '字段编码，如 leaf_shape',
    field_label VARCHAR(100) NOT NULL COMMENT '展示名，如 叶形',
    field_type VARCHAR(20) NOT NULL COMMENT 'TEXT/TEXTAREA/NUMBER/SELECT/MULTI_SELECT/BOOLEAN/DATE',
    scope_type VARCHAR(20) NOT NULL DEFAULT 'GLOBAL' COMMENT '适用范围 GLOBAL/CATEGORY/SPECIES',
    scope_id BIGINT DEFAULT NULL COMMENT '适用对象ID（CATEGORY/SPECIES 时使用）',
    options_json TEXT DEFAULT NULL COMMENT '单选/多选可选值 JSON 数组',
    validation_json VARCHAR(500) DEFAULT NULL COMMENT '校验配置 JSON（最小/最大/正则等）',
    required TINYINT NOT NULL DEFAULT 0 COMMENT '提交时是否必填 1=是 0=否',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用 1=是 0=否（停用保留历史数据）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_by BIGINT DEFAULT NULL COMMENT '创建教师用户ID',
    version INT NOT NULL DEFAULT 1 COMMENT '字段配置版本，便于历史兼容',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_field_scope (scope_type, scope_id),
    KEY idx_field_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师动态植物描述项定义';

CREATE TABLE IF NOT EXISTS plant_field_value (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    observation_id BIGINT NOT NULL COMMENT '观察记录ID',
    field_id BIGINT NOT NULL COMMENT '字段定义ID',
    field_version INT DEFAULT NULL COMMENT '提交时字段定义版本快照',
    value_text VARCHAR(500) DEFAULT NULL COMMENT '文本/数字/日期序列化值',
    value_json TEXT DEFAULT NULL COMMENT '多选等复杂值 JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_field_value_observation (observation_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察记录动态描述值';
