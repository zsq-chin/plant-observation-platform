package com.jingxuan.plant.vo;

import lombok.Data;

/** 省份植物目录行。 */
@Data
public class ProvinceSpeciesStatVO {

    private Long speciesId;
    private String commonName;
    private String scientificName;
    private String categoryName;
    private String familyName;
    private String coverUrl;
    private Long observationCount;
    private Long studentCount;
    private Double averageRating;
}
