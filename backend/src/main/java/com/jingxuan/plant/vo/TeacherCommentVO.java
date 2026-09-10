package com.jingxuan.plant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 首页最新教师点评条目。 */
@Data
public class TeacherCommentVO {

    private Long commentId;
    private Long observationId;
    private String plantName;
    private String teacherName;
    private String content;
    private LocalDateTime createTime;
}
