package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 教师审核决定。 */
public record ReviewDecisionRequest(
        @NotBlank String action,
        @Size(max = 500) String comment,
        Long bindSpeciesId
) {}
