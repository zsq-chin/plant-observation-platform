-- ============================================================
-- V7: 植物域核心表（全国植物观察与交流平台）
--   1. sys_region             行政区划（省/市/区县）
--   2. plant_category         植物类别/生活型
--   3. plant_species          标准植物物种库
--   4. plant_observation      学生观察记录（核心）
--   5. plant_photo            观察记录多图
-- 说明: 对齐《详细设计说明书》第 8 章；沿用 BaseEntity 通用列
--       (id 雪花 / create_time / update_time / deleted 逻辑删除)。
--       旧 work 域表保留，不在本迁移内删除。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_region (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    region_code VARCHAR(12) NOT NULL COMMENT '行政区划代码',
    region_name VARCHAR(50) NOT NULL COMMENT '行政区划名称',
    parent_code VARCHAR(12) DEFAULT NULL COMMENT '上级区划代码',
    region_level VARCHAR(20) NOT NULL COMMENT '层级: COUNTRY/PROVINCE/CITY/DISTRICT',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用 1=是 0=否',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_region_code (region_code),
    KEY idx_region_parent (parent_code),
    KEY idx_region_level (region_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划';

CREATE TABLE IF NOT EXISTS plant_category (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    name VARCHAR(50) NOT NULL COMMENT '类别名称，如 乔木/灌木/草本/藤本',
    code VARCHAR(50) NOT NULL COMMENT '类别编码',
    description VARCHAR(255) DEFAULT NULL COMMENT '说明',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用 1=是 0=否',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_category_code (code),
    KEY idx_category_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植物类别/生活型';

CREATE TABLE IF NOT EXISTS plant_species (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    common_name VARCHAR(100) NOT NULL COMMENT '中文名，如 银杏',
    scientific_name VARCHAR(150) DEFAULT NULL COMMENT '学名，如 Ginkgo biloba',
    alias_names VARCHAR(255) DEFAULT NULL COMMENT '别名（逗号分隔）',
    family_name VARCHAR(100) DEFAULT NULL COMMENT '科',
    genus_name VARCHAR(100) DEFAULT NULL COMMENT '属',
    species_name VARCHAR(100) DEFAULT NULL COMMENT '种名（可选细分）',
    category_id BIGINT DEFAULT NULL COMMENT '植物类别ID（关联 plant_category）',
    description TEXT DEFAULT NULL COMMENT '标准物种介绍',
    cover_url VARCHAR(500) DEFAULT NULL COMMENT '默认封面',
    source VARCHAR(100) DEFAULT NULL COMMENT '数据来源/参考',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用 1=是 0=否',
    created_by BIGINT DEFAULT NULL COMMENT '创建人用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_species_common_name (common_name),
    KEY idx_species_scientific_name (scientific_name),
    KEY idx_species_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准植物物种库';

CREATE TABLE IF NOT EXISTS plant_observation (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    submitter_id BIGINT NOT NULL COMMENT '提交学生用户ID',
    class_id BIGINT DEFAULT NULL COMMENT '提交时班级ID（快照）',
    class_name_snapshot VARCHAR(50) DEFAULT NULL COMMENT '提交时班级名称快照',
    species_id BIGINT DEFAULT NULL COMMENT '标准物种ID（草稿可空，提交前尽量绑定）',
    reported_common_name VARCHAR(100) DEFAULT NULL COMMENT '学生上报中文名',
    reported_scientific_name VARCHAR(150) DEFAULT NULL COMMENT '学生上报学名',
    category_id BIGINT DEFAULT NULL COMMENT '植物类别ID',
    province_code VARCHAR(12) DEFAULT NULL COMMENT '省级行政代码',
    province_name VARCHAR(50) DEFAULT NULL COMMENT '省名快照',
    city_code VARCHAR(12) DEFAULT NULL COMMENT '市级行政代码',
    city_name VARCHAR(50) DEFAULT NULL COMMENT '市名快照',
    district_code VARCHAR(12) DEFAULT NULL COMMENT '区县行政代码',
    district_name VARCHAR(50) DEFAULT NULL COMMENT '区县名快照',
    location_text VARCHAR(255) DEFAULT NULL COMMENT '学生手填详细地点（可选）',
    observed_at DATETIME DEFAULT NULL COMMENT '观察/采集时间',
    description TEXT DEFAULT NULL COMMENT '基础描述',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/REJECTED/APPROVED/OFFLINE',
    is_public TINYINT NOT NULL DEFAULT 0 COMMENT '是否公开 1=是 0=否（审核通过置1）',
    published_at DATETIME DEFAULT NULL COMMENT '发布时间',
    featured TINYINT NOT NULL DEFAULT 0 COMMENT '教师精选/优秀观察 1=是 0=否',
    featured_at DATETIME DEFAULT NULL COMMENT '精选时间',
    featured_by BIGINT DEFAULT NULL COMMENT '精选操作教师ID',
    view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    submit_time DATETIME DEFAULT NULL COMMENT '最近一次提交审核时间',
    approved_time DATETIME DEFAULT NULL COMMENT '最近一次审核通过时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_observation_status_province (status, province_code),
    KEY idx_observation_species (species_id, status),
    KEY idx_observation_submitter (submitter_id, status),
    KEY idx_observation_class (class_id, status),
    KEY idx_observation_category (category_id, status),
    KEY idx_observation_observed_at (observed_at),
    KEY idx_observation_gallery (status, is_public, featured, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生植物观察记录';

CREATE TABLE IF NOT EXISTS plant_photo (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    observation_id BIGINT NOT NULL COMMENT '所属观察记录ID',
    uploader_id BIGINT NOT NULL COMMENT '上传人用户ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问地址',
    thumbnail_url VARCHAR(500) DEFAULT NULL COMMENT '缩略图地址（暂同原图）',
    file_size BIGINT NOT NULL COMMENT '字节数',
    mime_type VARCHAR(100) DEFAULT NULL COMMENT 'MIME 类型',
    organ_type VARCHAR(20) DEFAULT NULL COMMENT '器官类型 WHOLE/LEAF/FLOWER/FRUIT/BARK/SEED/OTHER',
    is_cover TINYINT NOT NULL DEFAULT 0 COMMENT '是否封面 1=是 0=否',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
    PRIMARY KEY (id),
    KEY idx_photo_observation (observation_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察记录照片';
