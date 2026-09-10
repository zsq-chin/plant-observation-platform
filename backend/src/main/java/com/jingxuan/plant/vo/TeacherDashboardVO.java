package com.jingxuan.plant.vo;

import lombok.Data;

/** 教师工作台统计（V4 §81/教师效率）。 */
@Data
public class TeacherDashboardVO {
    private long pendingCount;
    private long todaySubmittedCount;
    private long todayApprovedCount;
    private long todayRejectedCount;
}
