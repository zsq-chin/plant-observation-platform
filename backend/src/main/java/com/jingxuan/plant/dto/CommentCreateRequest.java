package com.jingxuan.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 发表评论/回复请求。 */
public record CommentCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        Long parentId
) {}
