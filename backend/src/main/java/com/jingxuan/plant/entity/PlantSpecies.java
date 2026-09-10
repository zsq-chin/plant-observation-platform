package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 标准植物物种库条目（PlantSpecies 与 PlantObservation 严格分离）。 */
@Getter
@Setter
@TableName("plant_species")
public class PlantSpecies extends BaseEntity {

    private String commonName;
    private String scientificName;
    private String aliasNames;
    private String familyName;
    private String genusName;
    private String speciesName;
    private Long categoryId;
    private String description;
    private String coverUrl;
    private String source;
    private Integer enabled;
    private Long createdBy;
}
