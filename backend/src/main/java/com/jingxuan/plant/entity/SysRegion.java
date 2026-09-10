package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 行政区划（省/市/区县）。 */
@Getter
@Setter
@TableName("sys_region")
public class SysRegion extends BaseEntity {

    private String regionCode;
    private String regionName;
    private String parentCode;
    private String regionLevel;
    private Integer sortOrder;
    private Integer enabled;
    private java.math.BigDecimal centerLng;
    private java.math.BigDecimal centerLat;
    private java.math.BigDecimal minLng;
    private java.math.BigDecimal minLat;
    private java.math.BigDecimal maxLng;
    private java.math.BigDecimal maxLat;
}