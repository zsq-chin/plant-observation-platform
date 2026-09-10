package com.jingxuan.plant.vo;

import lombok.Data;

import java.util.List;

/** 省内可视化数据（V3 §35-38）：只统计 APPROVED 且 is_public=1。 */
@Data
public class ProvinceVisualizationVO {

    private ProvinceInfo province;
    private List<RegionInfo> regions;
    private List<TopSpeciesItem> topSpecies;

    @Data
    public static class ProvinceInfo {
        private String code;
        private String name;
        private long speciesCount;
        private long observationCount;
        private long studentCount;
    }

    @Data
    public static class RegionInfo {
        private String regionCode;
        private String regionName;
        private Double centerLng;
        private Double centerLat;
        private long speciesCount;
        private long observationCount;
        private long studentCount;
        private List<TopSpeciesItem> topSpecies;
    }

    @Data
    public static class TopSpeciesItem {
        private Long speciesId;
        private String commonName;
        private String coverUrl;
        private long observationCount;
    }
}
