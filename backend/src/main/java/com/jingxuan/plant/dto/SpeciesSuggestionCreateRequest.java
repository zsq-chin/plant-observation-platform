package com.jingxuan.plant.dto;

import jakarta.validation.constraints.Size;

/** 学生新物种建议请求（V4 §68）。 */
public record SpeciesSuggestionCreateRequest(
        @Size(max = 100) String suggestedCommonName,
        @Size(max = 150) String suggestedScientificName,
        @Size(max = 1000) String description,
        Long sampleObservationId
) {}
