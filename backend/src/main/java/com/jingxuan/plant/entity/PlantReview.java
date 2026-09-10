package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 教师审核历史（一次审核一条，永不覆盖）。 */
@Getter
@Setter
@TableName("plant_review")
public class PlantReview extends BaseEntity {

    private Long observationId;
    private Long reviewerId;
    /** APPROVED / REJECTED / NEED_SUPPLEMENT */
    private String action;
    private String comment;
    private String aiReference;
    private LocalDateTime reviewedAt;
}
