package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 学生一次植物观察记录（核心业务对象）。 */
@Getter
@Setter
@TableName("plant_observation")
public class PlantObservation extends BaseEntity {

    private Long submitterId;
    private Long classId;
    private String classNameSnapshot;
    private Long speciesId;
    private String reportedCommonName;
    private String reportedScientificName;
    private Long categoryId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String locationText;
    private LocalDateTime observedAt;
    private String description;
    /** DRAFT / SUBMITTED / REJECTED / APPROVED / OFFLINE */
    private String status;
    /** IDENTIFIED / PENDING（未知植物待鉴定，V4 §69） */
    private String identificationStatus;
    private Integer isPublic;
    private LocalDateTime publishedAt;
    private Integer featured;
    private LocalDateTime featuredAt;
    private Long featuredBy;
    private Integer viewCount;
    private LocalDateTime submitTime;
    private LocalDateTime approvedTime;
}