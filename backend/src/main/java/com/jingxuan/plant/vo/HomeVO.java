package com.jingxuan.plant.vo;

import lombok.Data;

import java.util.List;

/** 首页聚合数据。 */
@Data
public class HomeVO {

    private Statistics statistics;
    private List<GalleryItemVO> featuredObservations;
    private List<GalleryItemVO> latestObservations;
    /** 最新教师点评 */
    private List<TeacherCommentVO> teacherComments;

    @Data
    public static class Statistics {
        private long speciesCount;
        private long observationCount;
        private long studentCount;
        private long provinceCount;
        private long categoryCount;
    }
}
