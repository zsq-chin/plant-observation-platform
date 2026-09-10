package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 学生新物种建议（V4 §68）。 */
@Getter
@Setter
@TableName("plant_species_suggestion")
public class PlantSpeciesSuggestion extends BaseEntity {

    private Long submitterId;
    private String suggestedCommonName;
    private String suggestedScientificName;
    private String description;
    private Long sampleObservationId;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long linkedSpeciesId;
    private Long reviewerId;
    private String reviewComment;
}
