package com.jingxuan.plant.vo;

import lombok.Data;

/** 学生工作台统计（V4 §6）。 */
@Data
public class StudentDashboardVO {
    private long draftCount;
    private long submittedCount;
    private long approvedCount;
    private long rejectedCount;
    private long unreadNotificationCount;
}
