package com.jingxuan.plant.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 公开观察详情（审核通过且公开）。 */
@Data
public class ObservationPublicDetailVO {

    private Long observationId;
    private Long speciesId;
    private String commonName;
    private String scientificName;
    private Long categoryId;
    private String categoryName;
    private String familyName;
    private String genusName;
    private String reportedCommonName;
    private String reportedScientificName;
    private List<PhotoVO> photos;
    private String provinceName;
    private String cityName;
    private String districtName;
    private String locationText;
    private String submitterName;
    private String className;
    private LocalDateTime observedAt;
    private String description;
    private Boolean featured;
    private LocalDateTime publishedAt;
    private Integer viewCount;
    private Double averageRating;
    private Long ratingCount;
    private Long commentCount;
    private List<Map<String, Object>> fieldValues;
    /** 最近一次教师审核意见 */
    private String reviewComment;
    /** 鉴定状态 IDENTIFIED/PENDING（V4 §69） */
    private String identificationStatus;
    /** 数据质量提示（V4 §70） */
    private List<String> qualityWarnings;

    @Data
    public static class PhotoVO {
        private Long photoId;
        private String fileUrl;
        private String thumbnailUrl;
        private String organType;
        private Boolean isCover;
    }
}