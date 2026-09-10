package com.jingxuan.plant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 展廊卡片（首页/全部展廊共用，对象为 Observation）。 */
@Data
public class GalleryItemVO {

    private Long observationId;
    private Long speciesId;
    private String commonName;
    private String scientificName;
    private String reportedCommonName;
    private String categoryName;
    private String familyName;
    private String coverUrl;
    private String provinceCode;
    private String provinceName;
    private String cityName;
    private String submitterName;
    private String className;
    private LocalDateTime observedAt;
    private String description;
    private Boolean featured;
    private Double averageRating;
    private Long ratingCount;
    private Long commentCount;
    private Integer viewCount;
    private Long classId;
    private Long categoryId;
}
