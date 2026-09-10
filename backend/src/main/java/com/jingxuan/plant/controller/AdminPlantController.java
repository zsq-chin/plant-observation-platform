package com.jingxuan.plant.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import com.jingxuan.common.Result;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.dto.CategorySaveRequest;
import com.jingxuan.plant.dto.SpeciesSaveRequest;
import com.jingxuan.plant.entity.PlantCategory;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.service.PlantReviewService;
import com.jingxuan.plant.service.PlantSpeciesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 管理员植物平台接口：物种库/类别维护、观察记录治理。 */
@Tag(name = "植物平台-管理员端")
@RestController
@RequestMapping("/api/admin/plant")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlantController {

    private final PlantSpeciesService speciesService;
    private final PlantReviewService reviewService;
    private final PlantObservationMapper observationMapper;

    // ---------- 植物类别 ----------

    @Operation(summary = "类别列表")
    @GetMapping("/categories")
    public Result<List<PlantCategory>> categories() {
        return Result.ok(speciesService.categoryList());
    }

    @Operation(summary = "新增类别")
    @PostMapping("/categories")
    public Result<PlantCategory> createCategory(@Valid @RequestBody CategorySaveRequest req) {
        return Result.ok(speciesService.createCategory(req));
    }

    @Operation(summary = "修改类别")
    @PutMapping("/categories/{id}")
    public Result<PlantCategory> updateCategory(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest req) {
        return Result.ok(speciesService.updateCategory(id, req));
    }

    // ---------- 标准物种 ----------

    @Operation(summary = "物种列表")
    @GetMapping("/species")
    public Result<PageResult<PlantSpecies>> species(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long categoryId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(speciesService.search(keyword, categoryId, page, size));
    }

    @Operation(summary = "新增物种")
    @PostMapping("/species")
    public Result<PlantSpecies> createSpecies(@Valid @RequestBody SpeciesSaveRequest req) {
        return Result.ok(speciesService.createSpecies(req, currentUserId()));
    }

    @Operation(summary = "修改物种")
    @PutMapping("/species/{id}")
    public Result<PlantSpecies> updateSpecies(@PathVariable Long id, @Valid @RequestBody SpeciesSaveRequest req) {
        return Result.ok(speciesService.updateSpecies(id, req));
    }

    // ---------- 观察记录治理 ----------

    @Operation(summary = "观察记录列表（治理）")
    @GetMapping("/observations")
    public Result<PageResult<PlantObservation>> observations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long speciesId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(PageUtil.query(page, size, observationMapper, w -> w
                .eq(status != null && !status.isBlank(), PlantObservation::getStatus, status)
                .eq(classId != null, PlantObservation::getClassId, classId)
                .eq(speciesId != null, PlantObservation::getSpeciesId, speciesId)
                .orderByDesc(PlantObservation::getCreateTime)));
    }

    @Operation(summary = "强制下线（从展廊/地图移除）")
    @PostMapping("/observations/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        PlantObservation obs = observationMapper.selectById(id);
        if (obs == null) {
            return Result.fail("观察记录不存在");
        }
        obs.setStatus(PlantStatuses.OFFLINE);
        obs.setIsPublic(0);
        observationMapper.updateById(obs);
        return Result.ok();
    }

    @Operation(summary = "重新上架（恢复公开）")
    @PostMapping("/observations/{id}/online")
    public Result<Void> online(@PathVariable Long id) {
        PlantObservation obs = observationMapper.selectById(id);
        if (obs == null) {
            return Result.fail("观察记录不存在");
        }
        if (PlantStatuses.OFFLINE.equals(obs.getStatus())) {
            obs.setStatus(PlantStatuses.APPROVED);
            obs.setIsPublic(1);
            obs.setPublishedAt(LocalDateTime.now());
            observationMapper.updateById(obs);
        }
        return Result.ok();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.jingxuan.security.JwtUserDetails details) {
            return details.getUserId();
        }
        return com.jingxuan.security.SecurityUtils.requireCurrentUserId();
    }
}
