package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 教师处理物种建议决定（V4 §68）。 */
public record SuggestionDecisionRequest(
        @NotBlank String status,
        Long linkedSpeciesId,
        @Size(max = 500) String comment
) {}
