package com.jingxuan.plant.vo;

import lombok.Data;

/** 全国地图省份聚合行。 */
@Data
public class MapChinaStatVO {

    private String provinceCode;
    private String provinceName;
    private Long speciesCount;
    private Long observationCount;
    private Long studentCount;
}
