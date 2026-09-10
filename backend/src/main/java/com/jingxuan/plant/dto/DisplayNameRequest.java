package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学生公开展示花名设置请求（V4 下一步开发计划 §5.1）。 */
public record DisplayNameRequest(
        @NotBlank @Size(max = 32) String displayName
) {}
