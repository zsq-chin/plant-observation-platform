package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 植物类别保存请求（管理员）。 */
public record CategorySaveRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 50) String code,
        @Size(max = 255) String description,
        Integer sortOrder,
        Integer enabled
) {}
