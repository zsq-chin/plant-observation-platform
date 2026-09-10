package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 教师批量审核请求（V4 §77）。 */
public record BatchReviewRequest(
        @NotEmpty List<Long> observationIds,
        @NotBlank String action,
        @Size(max = 500) String comment
) {}
