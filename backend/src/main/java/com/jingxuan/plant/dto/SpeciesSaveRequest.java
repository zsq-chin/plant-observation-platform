package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 标准物种保存请求（管理员）。 */
public record SpeciesSaveRequest(
        @NotBlank @Size(max = 100) String commonName,
        @Size(max = 150) String scientificName,
        @Size(max = 255) String aliasNames,
        @Size(max = 100) String familyName,
        @Size(max = 100) String genusName,
        @Size(max = 100) String speciesName,
        Long categoryId,
        @Size(max = 5000) String description,
        @Size(max = 500) String coverUrl,
        @Size(max = 100) String source,
        Integer enabled
) {}
