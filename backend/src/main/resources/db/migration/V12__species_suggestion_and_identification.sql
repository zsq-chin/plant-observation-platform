-- ============================================================
-- V12: 物种建议与未知植物（V4 §68-69）
--   1. plant_species_suggestion  学生提交“找不到的植物”建议
--   2. plant_observation.identification_status 未知/待鉴定标记
-- ============================================================
ALTER TABLE plant_observation
    ADD COLUMN identification_status VARCHAR(20) DEFAULT 'IDENTIFIED' COMMENT '鉴定状态: IDENTIFIED/PENDING(待鉴定)';

CREATE TABLE IF NOT EXISTS plant_species_suggestion (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    submitter_id BIGINT NOT NULL COMMENT '建议学生用户ID',
    suggested_common_name VARCHAR(100) DEFAULT NULL COMMENT '建议中文名',
    suggested_scientific_name VARCHAR(150) DEFAULT NULL COMMENT '建议学名',
    description VARCHAR(1000) DEFAULT NULL COMMENT '特征/场景描述',
    sample_observation_id BIGINT DEFAULT NULL COMMENT '样例观察记录ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    linked_species_id BIGINT DEFAULT NULL COMMENT '处理时绑定/新建的标准物种ID',
    reviewer_id BIGINT DEFAULT NULL COMMENT '处理教师ID',
    review_comment VARCHAR(500) DEFAULT NULL COMMENT '处理意见',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_suggestion_submitter (submitter_id),
    KEY idx_suggestion_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新物种建议（学生提交，教师处理）';
