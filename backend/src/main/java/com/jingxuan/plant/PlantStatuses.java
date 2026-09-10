package com.jingxuan.plant;

/**
 * 植物观察记录状态常量（与 V7 迁移注释、状态机保持一致）。
 */
public final class PlantStatuses {

    private PlantStatuses() {}

    /** 草稿：可编辑，信息可不完整 */
    public static final String DRAFT = "DRAFT";
    /** 已提交：等待教师审核 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 已驳回：学生可修改后重新提交 */
    public static final String REJECTED = "REJECTED";
    /** 审核通过：进入展廊/地图公开数据 */
    public static final String APPROVED = "APPROVED";
    /** 下线：管理员强制下线，从公开数据移除 */
    public static final String OFFLINE = "OFFLINE";
}
