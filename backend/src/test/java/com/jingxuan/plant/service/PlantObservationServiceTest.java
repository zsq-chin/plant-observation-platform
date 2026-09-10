package com.jingxuan.plant.service;

import com.jingxuan.common.BaseEntity;
import com.jingxuan.entity.SysDict;
import com.jingxuan.entity.SysUser;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysDictMapper;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.dto.ObservationSaveRequest;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.mapper.PlantFieldDefinitionMapper;
import com.jingxuan.plant.mapper.PlantFieldValueMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 观察记录学生端规则测试（见改造文档 24.1 ObservationService）。 */
class PlantObservationServiceTest {

    private final PlantObservationMapper observationMapper = mock(PlantObservationMapper.class);
    private final PlantSpeciesMapper speciesMapper = mock(PlantSpeciesMapper.class);
    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final SysDictMapper sysDictMapper = mock(SysDictMapper.class);
    private final PlantRegionService regionService = mock(PlantRegionService.class);
    private final PlantPhotoService photoService = mock(PlantPhotoService.class);
    private final PlantFieldDefinitionMapper fieldDefinitionMapper = mock(PlantFieldDefinitionMapper.class);
    private final PlantFieldValueMapper fieldValueMapper = mock(PlantFieldValueMapper.class);

    private final PlantObservationService service = new PlantObservationService(
            observationMapper, speciesMapper, sysUserMapper, sysDictMapper,
            regionService, photoService, fieldDefinitionMapper, fieldValueMapper);

    private static PlantObservation observation(Long id, Long submitterId, String status) {
        PlantObservation obs = new PlantObservation();
        obs.setId(id);
        obs.setSubmitterId(submitterId);
        obs.setStatus(status);
        return obs;
    }

    @Test
    void createDraftBindsOwnerAndClassSnapshot() {
        SysUser student = new SysUser();
        student.setId(100L);
        student.setClassId(5L);
        SysDict clazz = new SysDict();
        clazz.setDictLabel("1班");
        when(sysUserMapper.selectById(100L)).thenReturn(student);
        when(sysDictMapper.selectById(5L)).thenReturn(clazz);
        when(regionService.resolveName("330000")).thenReturn("浙江省");

        service.createDraft(new ObservationSaveRequest(null, "银杏", null,
                "330000", "330100", "330106", "校园",
                LocalDateTime.of(2026, 9, 8, 10, 0), "描述", null, false), 100L);

        ArgumentCaptor<PlantObservation> captor = ArgumentCaptor.forClass(PlantObservation.class);
        verify(observationMapper).insert(captor.capture());
        PlantObservation saved = captor.getValue();
        assertEquals(100L, saved.getSubmitterId());
        assertEquals(PlantStatuses.DRAFT, saved.getStatus());
        assertEquals("1班", saved.getClassNameSnapshot());
        assertEquals("浙江省", saved.getProvinceName());
        verify(fieldValueMapper).delete(any());
    }

    @Test
    void updateRejectedDraftAllowedButApprovedForbidden() {
        PlantObservation rejected = observation(1L, 100L, PlantStatuses.REJECTED);
        PlantObservation approved = observation(2L, 100L, PlantStatuses.APPROVED);
        when(observationMapper.selectById(1L)).thenReturn(rejected);
        when(observationMapper.selectById(2L)).thenReturn(approved);

        service.update(1L, new ObservationSaveRequest(null, null, null, null, null, null, null, null, null, null, false), 100L);
        verify(observationMapper).updateById(rejected);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.update(2L, new ObservationSaveRequest(null, null, null, null, null, null, null, null, null, null, false), 100L));
        assertTrue(ex.getMessage().contains("不允许修改"));
    }

    @Test
    void studentCannotTouchOthersObservation() {
        when(observationMapper.selectById(1L)).thenReturn(observation(1L, 200L, PlantStatuses.DRAFT));
        assertThrows(BusinessException.class, () -> service.getMine(1L, 100L));
    }

    @Test
    void submitRequiresPhotoProvinceAndPlant() {
        PlantObservation draft = observation(1L, 100L, PlantStatuses.DRAFT);
        when(observationMapper.selectById(1L)).thenReturn(draft);
        when(fieldDefinitionMapper.selectList(any())).thenReturn(java.util.List.of());

        BusinessException noPhoto = assertThrows(BusinessException.class, () -> service.submit(1L, 100L));
        assertTrue(noPhoto.getMessage().contains("至少上传1张"));

        when(photoService.countPhotos(1L)).thenReturn(1L);
        BusinessException noProvince = assertThrows(BusinessException.class, () -> service.submit(1L, 100L));
        assertTrue(noProvince.getMessage().contains("省份"));

        draft.setProvinceCode("330000");
        BusinessException noPlant = assertThrows(BusinessException.class, () -> service.submit(1L, 100L));
        assertTrue(noPlant.getMessage().contains("植物"));

        draft.setReportedCommonName("银杏");
        draft.setObservedAt(LocalDateTime.now());
        service.submit(1L, 100L);
        assertEquals(PlantStatuses.SUBMITTED, draft.getStatus());
        assertEquals(LocalDateTime.now().toLocalDate(), draft.getSubmitTime().toLocalDate());
    }

    @Test
    void cannotSubmitWhenAlreadyApproved() {
        PlantObservation approved = observation(1L, 100L, PlantStatuses.APPROVED);
        when(observationMapper.selectById(1L)).thenReturn(approved);
        assertThrows(BusinessException.class, () -> service.submit(1L, 100L));
        verify(observationMapper, never()).updateById(org.mockito.ArgumentMatchers.<PlantObservation>any());
    }
}