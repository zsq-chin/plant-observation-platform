-- V13: 学生公开展示花名（display_name，V4 下一步开发计划 §5.1）
-- 登录与后台管理仍使用真实身份；公开端优先展示花名，未设置时按 plant.privacy.show-real-name 策略回退。
ALTER TABLE sys_user
    ADD COLUMN display_name VARCHAR(32) NULL COMMENT '公开展示花名（可为空）' AFTER real_name;
