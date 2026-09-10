package com.jingxuan.plant.controller;

import com.jingxuan.common.Result;
import com.jingxuan.plant.dto.CommentCreateRequest;
import com.jingxuan.plant.dto.RatingSaveRequest;
import com.jingxuan.plant.entity.PlantComment;
import com.jingxuan.plant.entity.PlantRating;
import com.jingxuan.plant.service.PlantCommunityService;
import com.jingxuan.security.JwtUserDetails;
import com.jingxuan.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** 社区互动接口（评论/回复/评分/教师治理，需登录）。 */
@Tag(name = "植物平台-社区")
@RestController
@RequestMapping("/api/community/plant")
@RequiredArgsConstructor
public class CommunityPlantController {

    private final PlantCommunityService communityService;

    @Operation(summary = "发表评论/回复")
    @PostMapping("/observations/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<PlantComment> comment(@PathVariable Long id, @Valid @RequestBody CommentCreateRequest req) {
        return Result.ok(communityService.addComment(id, currentUserId(), currentRoleCode(), req.content(), req.parentId()));
    }

    @Operation(summary = "删除评论（作者或管理员）")
    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        communityService.deleteComment(commentId, currentUserId(), isAdmin());
        return Result.ok();
    }

    @Operation(summary = "提交/更新本人评分")
    @PostMapping("/observations/{id}/rating")
    @PreAuthorize("isAuthenticated()")
    public Result<PlantRating> rate(@PathVariable Long id, @Valid @RequestBody RatingSaveRequest req) {
        return Result.ok(communityService.upsertRating(id, currentUserId(), req.score()));
    }

    @Operation(summary = "教师/管理员隐藏评论")
    @PostMapping("/comments/{commentId}/hide")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Void> hide(@PathVariable Long commentId) {
        communityService.hideComment(commentId);
        return Result.ok();
    }

    @Operation(summary = "教师点评置顶/取消置顶")
    @PostMapping("/comments/{commentId}/pin")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Void> pin(@PathVariable Long commentId, @RequestParam(defaultValue = "true") boolean pinned) {
        communityService.pinComment(commentId, currentUserId(), isAdmin(), pinned);
        return Result.ok();
    }

    private Long currentUserId() {
        return SecurityUtils.requireCurrentUserId();
    }

    private String currentRoleCode() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserDetails details) {
            return details.getRoleCode();
        }
        return null;
    }

    private boolean isAdmin() {
        return "ROLE_ADMIN".equals(currentRoleCode());
    }
}
