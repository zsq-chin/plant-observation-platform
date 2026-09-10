package com.jingxuan.plant.vo;

import lombok.Data;

import java.util.List;

/** 全局搜索结果（V4 §93-94）。 */
@Data
public class PlantSearchResultVO {
    private List<com.jingxuan.plant.entity.PlantSpecies> species;
    private List<GalleryItemVO> observations;
}
