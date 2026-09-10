package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 动态字段值条目。 */
public record FieldValueEntry(
        @NotNull Long fieldId,
        @Size(max = 500) String valueText,
        @Size(max = 4000) String valueJson
) {}
