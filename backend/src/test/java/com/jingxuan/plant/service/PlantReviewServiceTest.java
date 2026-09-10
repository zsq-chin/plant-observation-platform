package com.jingxuan.plant.service;

import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.dto.ReviewDecisionRequest;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantReview;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantReviewMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 教师审核规则测试（见改造文档 24.1 ReviewService）。 */
class PlantReviewServiceTest {

    private final PlantObservationMapper observationMapper = mock(PlantObservationMapper.class);
    private final PlantReviewMapper reviewMapper = mock(PlantReviewMapper.class);
    private final PlantSpeciesMapper speciesMapper = mock(PlantSpeciesMapper.class);
    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final PlantPhotoService photoService = mock(PlantPhotoService.class);

    private final PlantReviewService service = new PlantReviewService(
            observationMapper, reviewMapper, speciesMapper, sysUserMapper, photoService);

    private static PlantObservation submitted(Long id) {
        PlantObservation obs = new PlantObservation();
        obs.setId(id);
        obs.setStatus(PlantStatuses.SUBMITTED);
        return obs;
    }

    @Test
    void approvePublishesObservationAndKeepsReviewHistory() {
        PlantObservation obs = submitted(9L);
        when(observationMapper.selectById(9L)).thenReturn(obs);

        service.approve(9L, new ReviewDecisionRequest("APPROVED", "照片清晰，地点完整", null), 300L);

        assertEquals(PlantStatuses.APPROVED, obs.getStatus());
        assertEquals(1, obs.getIsPublic());
        assertTrue(obs.getPublishedAt() != null);
        assertTrue(obs.getApprovedTime() != null);
        ArgumentCaptor<PlantReview> captor = ArgumentCaptor.forClass(PlantReview.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals("APPROVED", captor.getValue().getAction());
        assertEquals(300L, captor.getValue().getReviewerId());
    }

    @Test
    void rejectRequiresReason() {
        when(observationMapper.selectById(9L)).thenReturn(submitted(9L));
        assertThrows(BusinessException.class, () ->
                service.reject(9L, new ReviewDecisionRequest("REJECTED", "  ", null), 300L));
        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.<PlantReview>any());
    }

    @Test
    void reviewOnlyWorksOnSubmittedState() {
        PlantObservation draft = new PlantObservation();
        draft.setId(1L);
        draft.setStatus(PlantStatuses.DRAFT);
        when(observationMapper.selectById(1L)).thenReturn(draft);
        assertThrows(BusinessException.class, () ->
                service.approve(1L, new ReviewDecisionRequest("APPROVED", null, null), 300L));
        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.<PlantReview>any());
    }

    @Test
    void featuredOnlyAllowedOnApprovedObservation() {
        PlantObservation obs = submitted(9L);
        when(observationMapper.selectById(9L)).thenReturn(obs);
        assertThrows(BusinessException.class, () -> service.setFeatured(9L, true, 300L));

        obs.setStatus(PlantStatuses.APPROVED);
        service.setFeatured(9L, true, 300L);
        assertEquals(1, obs.getFeatured());
        assertEquals(300L, obs.getFeaturedBy());
    }
}
