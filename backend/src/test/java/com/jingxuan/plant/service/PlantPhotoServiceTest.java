package com.jingxuan.plant.service;

import com.jingxuan.exception.BusinessException;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import com.jingxuan.plant.storage.PlantPhotoStorageService;
import com.jingxuan.plant.storage.PlantStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 照片：封面/排序/器官标签/删除规则（V4 App 编辑页依赖）。 */
class PlantPhotoServiceTest {

    private final PlantObservationMapper observationMapper = mock(PlantObservationMapper.class);
    private final PlantPhotoMapper photoMapper = mock(PlantPhotoMapper.class);
    private final PlantPhotoStorageService storageService = mock(PlantPhotoStorageService.class);
    private final PlantStorageProperties props = new PlantStorageProperties();

    private final PlantPhotoService service = new PlantPhotoService(photoMapper, observationMapper, storageService, props);

    private PlantObservation obs;
    private PlantPhoto photo;

    @BeforeEach
    void setUp() {
        props.setMaxPhotoCount(10);
        obs = new PlantObservation();
        obs.setId(100L);
        obs.setSubmitterId(7L);
        obs.setStatus("DRAFT");
        photo = new PlantPhoto();
        photo.setId(200L);
        photo.setObservationId(100L);
        photo.setOrganType("WHOLE");
        when(observationMapper.selectById(100L)).thenReturn(obs);
        when(photoMapper.selectById(200L)).thenReturn(photo);
    }

    @Test
    void updateOrganPersistsNormalizedType() {
        service.updateOrgan(100L, 200L, "leaf", 7L);
        ArgumentCaptor<PlantPhoto> captor = ArgumentCaptor.forClass(PlantPhoto.class);
        verify(photoMapper).updateById(captor.capture());
        assertEquals("LEAF", captor.getValue().getOrganType());
    }

    @Test
    void updateOrganRejectsForeignRecord() {
        assertThrows(BusinessException.class, () -> service.updateOrgan(100L, 200L, "LEAF", 8L));
        verify(photoMapper, never()).updateById(org.mockito.ArgumentMatchers.<PlantPhoto>any());
    }

    @Test
    void updateOrganRejectsWhenNotDraftOrRejected() {
        obs.setStatus("APPROVED");
        assertThrows(BusinessException.class, () -> service.updateOrgan(100L, 200L, "LEAF", 7L));
        verify(photoMapper, never()).updateById(org.mockito.ArgumentMatchers.<PlantPhoto>any());
    }

    @Test
    void setCoverClearsOtherCovers() {
        PlantPhoto other = new PlantPhoto();
        other.setId(201L);
        other.setObservationId(100L);
        other.setIsCover(1);
        when(photoMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PlantPhoto>>any()))
                .thenReturn(java.util.List.of(other));
        service.setCover(100L, 200L, 7L);
        verify(photoMapper).updateById(other);
        assertEquals(0, other.getIsCover());
        assertEquals(1, photo.getIsCover());
    }
}
