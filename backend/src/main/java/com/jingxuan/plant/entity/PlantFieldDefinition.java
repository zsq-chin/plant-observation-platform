package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 教师动态植物描述项定义。 */
@Getter
@Setter
@TableName("plant_field_definition")
public class PlantFieldDefinition extends BaseEntity {

    private String fieldCode;
    private String fieldLabel;
    /** TEXT / TEXTAREA / NUMBER / SELECT / MULTI_SELECT / BOOLEAN / DATE */
    private String fieldType;
    /** GLOBAL / CATEGORY / SPECIES */
    private String scopeType;
    private Long scopeId;
    private String optionsJson;
    private String validationJson;
    private Integer required;
    private Integer enabled;
    private Integer sortOrder;
    private Long createdBy;
    private Integer version;
}
