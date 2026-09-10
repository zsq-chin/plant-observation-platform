package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 观察记录动态描述值。 */
@Getter
@Setter
@TableName("plant_field_value")
public class PlantFieldValue extends BaseEntity {

    private Long observationId;
    private Long fieldId;
    private Integer fieldVersion;
    private String valueText;
    private String valueJson;
}
