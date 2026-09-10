-- ============================================================
-- V9: 审核与社区表
--   1. plant_review    教师审核历史（每次留痕，不覆盖）
--   2. plant_comment   观察记录评论/回复（parent_id 支持回复）
--   3. plant_rating    1~5 星评价（一人一记录一条）
-- ============================================================

CREATE TABLE IF NOT EXISTS plant_review (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    observation_id BIGINT NOT NULL COMMENT '观察记录ID',
    reviewer_id BIGINT NOT NULL COMMENT '审核人用户ID',
    action VARCHAR(20) NOT NULL COMMENT 'APPROVED/REJECTED/NEED_SUPPLEMENT',
    comment VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
    ai_reference TEXT DEFAULT NULL COMMENT 'AI 辅助参考（预留）',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_review_observation (observation_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师审核历史';

CREATE TABLE IF NOT EXISTS plant_comment (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    observation_id BIGINT NOT NULL COMMENT '观察记录ID',
    user_id BIGINT NOT NULL COMMENT '评论人用户ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父评论ID（回复时）',
    root_id BIGINT DEFAULT NULL COMMENT '根评论ID',
    content VARCHAR(1000) NOT NULL COMMENT '评论内容',
    is_teacher_comment TINYINT NOT NULL DEFAULT 0 COMMENT '是否教师身份 1=是 0=否',
    is_pinned TINYINT NOT NULL DEFAULT 0 COMMENT '教师点评置顶 1=是 0=否',
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/HIDDEN/DELETED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_comment_observation (observation_id, status, create_time),
    KEY idx_comment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察记录评论/回复';

CREATE TABLE IF NOT EXISTS plant_rating (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    observation_id BIGINT NOT NULL COMMENT '观察记录ID',
    user_id BIGINT NOT NULL COMMENT '评分人用户ID',
    score TINYINT NOT NULL COMMENT '1~5 星',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rating_obs_user (observation_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察记录星级评价';
