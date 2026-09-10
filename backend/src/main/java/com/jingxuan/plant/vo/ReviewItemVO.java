package com.jingxuan.plant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 教师审核列表行。 */
@Data
public class ReviewItemVO {

    private Long observationId;
    private String coverUrl;
    private Long speciesId;
    private String commonName;
    private String reportedCommonName;
    private String submitterName;
    private Long submitterId;
    private String className;
    private String provinceName;
    private String cityName;
    private String districtName;
    private LocalDateTime observedAt;
    private LocalDateTime submitTime;
    private String status;
    private Integer photoCount;
    private Long classId;
}
