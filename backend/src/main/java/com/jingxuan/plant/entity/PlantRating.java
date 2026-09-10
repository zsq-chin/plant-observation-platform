package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 观察记录 1~5 星评价（一人一记录一条）。 */
@Getter
@Setter
@TableName("plant_rating")
public class PlantRating extends BaseEntity {

    private Long observationId;
    private Long userId;
    private Integer score;
}
