package com.jingxuan.plant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 星级评分请求（1~5）。 */
public record RatingSaveRequest(
        @NotNull @Min(1) @Max(5) Integer score
) {}
