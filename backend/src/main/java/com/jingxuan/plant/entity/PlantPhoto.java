package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 观察记录照片（一条记录多张）。 */
@Getter
@Setter
@TableName("plant_photo")
public class PlantPhoto extends BaseEntity {

    private Long observationId;
    private Long uploaderId;
    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;
    private Long fileSize;
    private String mimeType;
    /** WHOLE / LEAF / FLOWER / FRUIT / BARK / SEED / OTHER */
    private String organType;
    private Integer isCover;
    private Integer sortOrder;
}
