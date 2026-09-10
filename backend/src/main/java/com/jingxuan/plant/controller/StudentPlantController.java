package com.jingxuan.plant.controller;

import com.jingxuan.common.PageResult;
import com.jingxuan.common.Result;
import com.jingxuan.plant.dto.ObservationSaveRequest;
import com.jingxuan.plant.entity.PlantFieldDefinition;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.service.PlantFieldService;
import com.jingxuan.plant.service.PlantObservationService;
import com.jingxuan.plant.service.PlantPhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 学生端植物观察记录接口。 */
@Tag(name = "植物平台-学生端")
@RestController
@RequestMapping("/api/student/plant")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
public class StudentPlantController {

    private final PlantObservationService observationService;
    private final PlantPhotoService photoService;
    private final PlantFieldService fieldService;
    private final com.jingxuan.plant.service.PlantDashboardService dashboardService;
    private final com.jingxuan.plant.service.PlantSuggestionService suggestionService;
    private final com.jingxuan.plant.service.PlantProfileService profileService;

    @Operation(summary = "创建观察记录草稿")
    @PostMapping("/observations")
    public Result<PlantObservation> create(@Valid @RequestBody ObservationSaveRequest req) {
        PlantObservation obs = observationService.createDraft(req, currentUserId());
        return Result.ok(obs);
    }

    @Operation(summary = "我的观察记录列表")
    @GetMapping("/observations")
    public Result<PageResult<PlantObservation>> myList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(observationService.listMine(currentUserId(), status, page, size));
    }

    @Operation(summary = "我的记录详情")
    @GetMapping("/observations/{id}")
    public Result<PlantObservation> detail(@PathVariable Long id) {
        return Result.ok(observationService.getMine(id, currentUserId()));
    }

    @Operation(summary = "保存/修改草稿或驳回记录")
    @PutMapping("/observations/{id}")
    public Result<PlantObservation> update(@PathVariable Long id, @Valid @RequestBody ObservationSaveRequest req) {
        return Result.ok(observationService.update(id, req, currentUserId()));
    }

    @Operation(summary = "提交审核")
    @PostMapping("/observations/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        observationService.submit(id, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "撤回已提交（未审核前）")
    @PostMapping("/observations/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        observationService.withdraw(id, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "删除草稿/驳回记录")
    @DeleteMapping("/observations/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        observationService.deleteDraft(id, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "上传植物照片")
    @PostMapping("/observations/{id}/photos")
    public Result<PlantPhoto> uploadPhoto(@PathVariable Long id,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) String organType) {
        PlantObservation obs = observationService.getMine(id, currentUserId());
        if (!"DRAFT".equals(obs.getStatus()) && !"REJECTED".equals(obs.getStatus())) {
            return Result.fail("当前状态不允许上传照片");
        }
        PlantPhoto photo = photoService.upload(id, currentUserId(), file, organType);
        return Result.ok(photo);
    }

    @Operation(summary = "删除照片")
    @DeleteMapping("/observations/{id}/photos/{photoId}")
    public Result<Void> deletePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        photoService.delete(id, photoId, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "设为封面")
    @PutMapping("/observations/{id}/photos/cover")
    public Result<Void> setCover(@PathVariable Long id, @RequestParam Long photoId) {
        photoService.setCover(id, photoId, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "调整照片顺序")
    @PutMapping("/observations/{id}/photos/order")
    public Result<Void> reorder(@PathVariable Long id, @RequestBody List<Long> photoIds) {
        photoService.reorder(id, photoIds, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "修改照片器官标签")
    @PutMapping("/observations/{id}/photos/{photoId}/organ")
    public Result<Void> updateOrgan(@PathVariable Long id, @PathVariable Long photoId,
                                    @RequestParam String organType) {
        photoService.updateOrgan(id, photoId, organType, currentUserId());
        return Result.ok();
    }

    @Operation(summary = "可填写的动态描述项")
    @GetMapping("/plant-fields")
    public Result<List<PlantFieldDefinition>> fields() {
        return Result.ok(fieldService.enabledFields());
    }

    @Operation(summary = "我的记录照片列表")
    @GetMapping("/observations/{id}/photos")
    public Result<List<PlantPhoto>> photos(@PathVariable Long id) {
        observationService.getMine(id, currentUserId());
        return Result.ok(photoService.listByObservation(id));
    }


    @Operation(summary = "我的公开展示花名")
    @GetMapping("/profile/display-name")
    public Result<String> myDisplayName() {
        return Result.ok(profileService.currentDisplayName(currentUserId()));
    }

    @Operation(summary = "设置公开展示花名（公开端展示用，登录与后台仍用真实身份）")
    @PutMapping("/profile/display-name")
    public Result<String> updateDisplayName(@Valid @RequestBody com.jingxuan.plant.dto.DisplayNameRequest req) {
        return Result.ok(profileService.updateDisplayName(currentUserId(), req.displayName()));
    }

    @Operation(summary = "学生工作台统计")
    @GetMapping("/dashboard")
    public Result<com.jingxuan.plant.vo.StudentDashboardVO> dashboard() {
        return Result.ok(dashboardService.student(currentUserId()));
    }

    @Operation(summary = "提交新物种建议")
    @PostMapping("/species-suggestions")
    public Result<com.jingxuan.plant.entity.PlantSpeciesSuggestion> createSuggestion(@Valid @RequestBody com.jingxuan.plant.dto.SpeciesSuggestionCreateRequest req) {
        return Result.ok(suggestionService.create(req, currentUserId()));
    }

    @Operation(summary = "我的物种建议列表")
    @GetMapping("/species-suggestions")
    public Result<com.jingxuan.common.PageResult<com.jingxuan.plant.entity.PlantSpeciesSuggestion>> mySuggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(suggestionService.listMine(currentUserId(), page, size));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.jingxuan.security.JwtUserDetails details) {
            return details.getUserId();
        }
        return com.jingxuan.security.SecurityUtils.requireCurrentUserId();
    }
}