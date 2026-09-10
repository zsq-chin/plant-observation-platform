package com.jingxuan.plant.service;

import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.modules.sensitive.service.DeepSeekReviewService;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantComment;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantRating;
import com.jingxuan.plant.mapper.PlantCommentMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantRatingMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 评论/评分规则测试（文档 24.1 CommentService 要点）。 */
class PlantCommunityServiceTest {

    private final PlantCommentMapper commentMapper = mock(PlantCommentMapper.class);
    private final PlantRatingMapper ratingMapper = mock(PlantRatingMapper.class);
    private final PlantObservationMapper observationMapper = mock(PlantObservationMapper.class);
    private final PlantSpeciesMapper speciesMapper = mock(PlantSpeciesMapper.class);
    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final DeepSeekReviewService deepSeekReviewService = mock(DeepSeekReviewService.class);
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
    private final org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);

    private final PlantCommunityService service = new PlantCommunityService(
            commentMapper, ratingMapper, observationMapper, speciesMapper, sysUserMapper, deepSeekReviewService, redisTemplate);

    { // 评论防刷计数默认为 1（首次）
        when(redisTemplate.opsForValue()).thenReturn((org.springframework.data.redis.core.ValueOperations) valueOps);
        when(valueOps.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
    }

    private static PlantObservation publicObservation(Long id) {
        PlantObservation obs = new PlantObservation();
        obs.setId(id);
        obs.setStatus(PlantStatuses.APPROVED);
        obs.setIsPublic(1);
        return obs;
    }

    @Test
    void commentOnUnpublishedObservationRejected() {
        PlantObservation draft = new PlantObservation();
        draft.setId(1L);
        draft.setStatus(PlantStatuses.DRAFT);
        when(observationMapper.selectById(1L)).thenReturn(draft);
        assertThrows(BusinessException.class, () ->
                service.addComment(1L, 100L, "ROLE_STUDENT", "很好看", null));
        verify(commentMapper, never()).insert(org.mockito.ArgumentMatchers.<PlantComment>any());
    }

    @Test
    void sensitiveCommentRejected() {
        when(observationMapper.selectById(1L)).thenReturn(publicObservation(1L));
        DeepSeekReviewService.ReviewResult fail = DeepSeekReviewService.ReviewResult.fail("sensitive_word", "违禁词");
        when(deepSeekReviewService.review(any(), any())).thenReturn(fail);
        assertThrows(BusinessException.class, () ->
                service.addComment(1L, 100L, "ROLE_STUDENT", "垃圾内容", null));
        verify(commentMapper, never()).insert(org.mockito.ArgumentMatchers.<PlantComment>any());
    }

    @Test
    void teacherCommentGetsIdentityFlag() {
        when(observationMapper.selectById(1L)).thenReturn(publicObservation(1L));
        when(deepSeekReviewService.review(any(), any()))
                .thenReturn(DeepSeekReviewService.ReviewResult.pass());
        service.addComment(1L, 300L, "ROLE_TEACHER", "这是一条专业点评", null);
        ArgumentCaptor<PlantComment> captor = ArgumentCaptor.forClass(PlantComment.class);
        verify(commentMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getIsTeacherComment());
    }

    @Test
    void ratingOutOfRangeRejectedAndUpdateKeepsSingleRow() {
        when(observationMapper.selectById(1L)).thenReturn(publicObservation(1L));
        assertThrows(BusinessException.class, () -> service.upsertRating(1L, 100L, 6));
        PlantRating existing = new PlantRating();
        existing.setId(7L);
        existing.setScore(3);
        when(ratingMapper.selectOne(any())).thenReturn(existing);
        service.upsertRating(1L, 100L, 5);
        assertEquals(5, existing.getScore());
        verify(ratingMapper, never()).insert(org.mockito.ArgumentMatchers.<PlantRating>any());
    }

    @Test
    void deleteCommentOnlyByOwnerOrAdmin() {
        PlantComment comment = new PlantComment();
        comment.setId(1L);
        comment.setUserId(200L);
        comment.setStatus("NORMAL");
        when(commentMapper.selectById(1L)).thenReturn(comment);
        assertThrows(BusinessException.class, () -> service.deleteComment(1L, 100L, false));
        service.deleteComment(1L, 100L, true);
        assertEquals("DELETED", comment.getStatus());
        assertTrue(comment.getStatus().length() > 0);
    }
}