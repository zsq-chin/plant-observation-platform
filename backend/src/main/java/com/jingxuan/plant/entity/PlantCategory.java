package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 植物类别/生活型（乔木、灌木、草本、藤本等）。 */
@Getter
@Setter
@TableName("plant_category")
public class PlantCategory extends BaseEntity {

    private String name;
    private String code;
    private String description;
    private Integer sortOrder;
    private Integer enabled;
}
