package com.jingxuan.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 学生创建/更新观察记录请求（草稿允许不完整）。 */
public record ObservationSaveRequest(
        Long speciesId,
        @Size(max = 100) String reportedCommonName,
        @Size(max = 150) String reportedScientificName,
        @Size(max = 12) String provinceCode,
        @Size(max = 12) String cityCode,
        @Size(max = 12) String districtCode,
        @Size(max = 255) String locationText,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime observedAt,
        @Size(max = 5000) String description,
        List<@jakarta.validation.Valid FieldValueEntry> fieldValues,
        /** 未知植物/待鉴定（V4 §69）：true 时允许不填物种与上报名称 */
        Boolean unknownPlant
) {}