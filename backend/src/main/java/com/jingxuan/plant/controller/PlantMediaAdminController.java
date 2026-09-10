package com.jingxuan.plant.controller;

import com.jingxuan.common.Result;
import com.jingxuan.plant.storage.PlantMediaConsistencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 植物图片存储管理接口（V3 §18）：只报告，不自动删除。 */
@Tag(name = "植物平台-图片存储管理")
@RestController
@RequestMapping("/api/admin/plant-media")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PlantMediaAdminController {

    private final PlantMediaConsistencyService mediaConsistencyService;

    @Operation(summary = "图片存储健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(mediaConsistencyService.health());
    }

    @Operation(summary = "图片存储一致性扫描")
    @GetMapping("/consistency")
    public Result<Map<String, Object>> consistency() {
        return Result.ok(mediaConsistencyService.consistency());
    }
}
