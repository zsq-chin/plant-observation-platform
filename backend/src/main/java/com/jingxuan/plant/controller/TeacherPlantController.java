package com.jingxuan.plant.controller;

import com.jingxuan.common.PageResult;
import com.jingxuan.common.Result;
import com.jingxuan.plant.dto.ReviewDecisionRequest;
import com.jingxuan.plant.entity.PlantFieldDefinition;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantReview;
import com.jingxuan.plant.service.PlantFieldService;
import com.jingxuan.plant.service.PlantGalleryService;
import com.jingxuan.plant.service.PlantReviewService;
import com.jingxuan.plant.vo.ObservationPublicDetailVO;
import com.jingxuan.plant.vo.ReviewItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 教师端植物审核接口。 */
@Tag(name = "植物平台-教师端")
@RestController
@RequestMapping("/api/teacher/plant")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherPlantController {

    private final PlantReviewService reviewService;
    private final PlantGalleryService galleryService;
    private final PlantFieldService fieldService;
    private final com.jingxuan.plant.service.PlantDashboardService dashboardService;
    private final com.jingxuan.plant.service.PlantSuggestionService suggestionService;

    @Operation(summary = "审核列表（默认待审核）")
    @GetMapping("/reviews")
    public Result<PageResult<ReviewItemVO>> reviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long speciesId,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.list(status, classId, speciesId, provinceCode, page, size));
    }

    @Operation(summary = "审核详情（含所有照片/字段）")
    @GetMapping("/reviews/{observationId}")
    public Result<ObservationPublicDetailVO> reviewDetail(@PathVariable Long observationId) {
        PlantObservation obs = reviewService.getObservation(observationId);
        return Result.ok(galleryService.assembleDetail(obs));
    }

    @Operation(summary = "审核历史")
    @GetMapping("/reviews/{observationId}/history")
    public Result<List<PlantReview>> reviewHistory(@PathVariable Long observationId) {
        return Result.ok(reviewService.history(observationId));
    }

    @Operation(summary = "审核通过（可选绑定标准物种/意见）")
    @PostMapping("/reviews/{observationId}/approve")
    public Result<Void> approve(@PathVariable Long observationId, @Valid @RequestBody ReviewDecisionRequest req) {
        reviewService.approve(observationId, req, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "驳回（必须填意见）")
    @PostMapping("/reviews/{observationId}/reject")
    public Result<Void> reject(@PathVariable Long observationId, @Valid @RequestBody ReviewDecisionRequest req) {
        reviewService.reject(observationId, req, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "推荐/取消优秀观察")
    @PostMapping("/observations/{observationId}/featured")
    public Result<Void> featured(@PathVariable Long observationId, @RequestParam(defaultValue = "true") boolean featured) {
        reviewService.setFeatured(observationId, featured, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "动态描述项列表（含停用）")
    @GetMapping("/plant-fields")
    public Result<List<PlantFieldDefinition>> fields() {
        return Result.ok(fieldService.allFields());
    }

    @Operation(summary = "新增动态描述项")
    @PostMapping("/plant-fields")
    public Result<PlantFieldDefinition> createField(
            @RequestParam String fieldCode,
            @RequestParam String fieldLabel,
            @RequestParam String fieldType,
            @RequestParam(required = false, defaultValue = "GLOBAL") String scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(required = false) String optionsJson,
            @RequestParam(required = false, defaultValue = "0") Integer required,
            @RequestParam(required = false, defaultValue = "0") Integer sortOrder) {
        return Result.ok(fieldService.create(fieldCode, fieldLabel, fieldType, scopeType, scopeId,
                optionsJson, required, sortOrder, currentUserId()));
    }

    @Operation(summary = "修改动态描述项")
    @PutMapping("/plant-fields/{id}")
    public Result<PlantFieldDefinition> updateField(@PathVariable Long id,
            @RequestParam String fieldCode,
            @RequestParam String fieldLabel,
            @RequestParam String fieldType,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(required = false) String optionsJson,
            @RequestParam(required = false) Integer required,
            @RequestParam(required = false) Integer sortOrder) {
        return Result.ok(fieldService.update(id, fieldCode, fieldLabel, fieldType, scopeType, scopeId,
                optionsJson, required, sortOrder));
    }

    @Operation(summary = "启用/停用动态描述项")
    @PostMapping("/plant-fields/{id}/enabled")
    public Result<Void> toggleField(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean enabled) {
        fieldService.toggleEnabled(id, enabled);
        return Result.ok();
    }


    @Operation(summary = "教师工作台统计")
    @GetMapping("/dashboard")
    public Result<com.jingxuan.plant.vo.TeacherDashboardVO> dashboard() {
        return Result.ok(dashboardService.teacher());
    }

    @Operation(summary = "批量审核")
    @PostMapping("/reviews/batch")
    public Result<Void> batchReview(@Valid @RequestBody com.jingxuan.plant.dto.BatchReviewRequest req) {
        reviewService.batchReview(req.observationIds(), req.action(), req.comment(), currentUserId());
        return Result.ok();
    }

    @Operation(summary = "待处理物种建议")
    @GetMapping("/species-suggestions")
    public Result<com.jingxuan.common.PageResult<com.jingxuan.plant.entity.PlantSpeciesSuggestion>> pendingSuggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(suggestionService.listPending(page, size));
    }

    @Operation(summary = "处理物种建议（通过/驳回）")
    @PostMapping("/species-suggestions/{id}/decide")
    public Result<com.jingxuan.plant.entity.PlantSpeciesSuggestion> decideSuggestion(
            @PathVariable Long id,
            @Valid @RequestBody com.jingxuan.plant.dto.SuggestionDecisionRequest req) {
        return Result.ok(suggestionService.decide(id, req, currentUserId()));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.jingxuan.security.JwtUserDetails details) {
            return details.getUserId();
        }
        return com.jingxuan.security.SecurityUtils.requireCurrentUserId();
    }
}