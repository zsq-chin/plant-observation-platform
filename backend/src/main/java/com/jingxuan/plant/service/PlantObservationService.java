package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import com.jingxuan.entity.SysDict;
import com.jingxuan.entity.SysUser;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysDictMapper;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.dto.FieldValueEntry;
import com.jingxuan.plant.dto.ObservationSaveRequest;
import com.jingxuan.plant.entity.PlantFieldDefinition;
import com.jingxuan.plant.entity.PlantFieldValue;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.mapper.PlantFieldDefinitionMapper;
import com.jingxuan.plant.mapper.PlantFieldValueMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 观察记录（学生端）：草稿创建/编辑、提交、撤回、删除、我的列表。 */
@Service
@RequiredArgsConstructor
public class PlantObservationService {

    private final PlantObservationMapper plantObservationMapper;
    private final PlantSpeciesMapper plantSpeciesMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDictMapper sysDictMapper;
    private final PlantRegionService regionService;
    private final PlantPhotoService photoService;
    private final PlantFieldDefinitionMapper fieldDefinitionMapper;
    private final PlantFieldValueMapper fieldValueMapper;

    @Transactional(rollbackFor = Exception.class)
    public PlantObservation createDraft(ObservationSaveRequest req, Long studentId) {
        PlantObservation obs = new PlantObservation();
        obs.setSubmitterId(studentId);
        obs.setStatus(PlantStatuses.DRAFT);
        obs.setIsPublic(0);
        obs.setFeatured(0);
        obs.setViewCount(0);
        obs.setIdentificationStatus("IDENTIFIED");
        applyRequest(obs, req);
        fillClassSnapshot(obs, studentId);
        plantObservationMapper.insert(obs);
        replaceFieldValues(obs.getId(), req == null ? null : req.fieldValues());
        return obs;
    }

    public PageResult<PlantObservation> listMine(Long studentId, String status, int page, int size) {
        return PageUtil.query(page, size, plantObservationMapper, w -> w
                .eq(PlantObservation::getSubmitterId, studentId)
                .eq(StringUtils.hasText(status), PlantObservation::getStatus, status)
                .orderByDesc(PlantObservation::getCreateTime));
    }

    public PlantObservation getMine(Long id, Long studentId) {
        PlantObservation obs = plantObservationMapper.selectById(id);
        if (obs == null || !obs.getSubmitterId().equals(studentId)) {
            throw new BusinessException("记录不存在或无权访问");
        }
        return obs;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantObservation update(Long id, ObservationSaveRequest req, Long studentId) {
        PlantObservation obs = getMine(id, studentId);
        if (!PlantStatuses.DRAFT.equals(obs.getStatus()) && !PlantStatuses.REJECTED.equals(obs.getStatus())) {
            throw new BusinessException("当前状态不允许修改");
        }
        applyRequest(obs, req);
        plantObservationMapper.updateById(obs);
        replaceFieldValues(obs.getId(), req == null ? null : req.fieldValues());
        return obs;
    }

    /** 提交审核：服务端完整性校验（照片/省份/植物），DRAFT/REJECTED 均可提交。 */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long studentId) {
        PlantObservation obs = getMine(id, studentId);
        if (!PlantStatuses.DRAFT.equals(obs.getStatus()) && !PlantStatuses.REJECTED.equals(obs.getStatus())) {
            throw new BusinessException("当前状态不允许提交审核");
        }
        validateBeforeSubmit(obs);
        obs.setStatus(PlantStatuses.SUBMITTED);
        obs.setSubmitTime(LocalDateTime.now());
        plantObservationMapper.updateById(obs);
    }

    /** 撤回：已提交且教师尚未审核时允许撤回为草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, Long studentId) {
        PlantObservation obs = getMine(id, studentId);
        if (!PlantStatuses.SUBMITTED.equals(obs.getStatus())) {
            throw new BusinessException("只有已提交的记录可以撤回");
        }
        obs.setStatus(PlantStatuses.DRAFT);
        plantObservationMapper.updateById(obs);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long id, Long studentId) {
        PlantObservation obs = getMine(id, studentId);
        if (!PlantStatuses.DRAFT.equals(obs.getStatus()) && !PlantStatuses.REJECTED.equals(obs.getStatus())) {
            throw new BusinessException("只有草稿或驳回记录可以删除");
        }
        plantObservationMapper.deleteById(id);
        // 级联清理照片行与本地物理文件（V3 §69）
        photoService.deleteAllForObservation(id);
    }

    public void fillClassSnapshot(PlantObservation obs, Long studentId) {
        SysUser user = sysUserMapper.selectById(studentId);
        if (user == null) {
            return;
        }
        obs.setClassId(user.getClassId());
        if (user.getClassId() != null) {
            SysDict dict = sysDictMapper.selectById(user.getClassId());
            obs.setClassNameSnapshot(dict != null ? dict.getDictLabel() : null);
        }
    }

    private void applyRequest(PlantObservation obs, ObservationSaveRequest req) {
        if (req == null) {
            return;
        }
        obs.setSpeciesId(req.speciesId());
        obs.setReportedCommonName(trim(req.reportedCommonName()));
        obs.setReportedScientificName(trim(req.reportedScientificName()));
        obs.setProvinceCode(trim(req.provinceCode()));
        obs.setCityCode(trim(req.cityCode()));
        obs.setDistrictCode(trim(req.districtCode()));
        obs.setLocationText(trim(req.locationText()));
        obs.setObservedAt(req.observedAt());
        obs.setDescription(trim(req.description()));
        if (Boolean.TRUE.equals(req.unknownPlant())) {
            obs.setIdentificationStatus("PENDING");
            obs.setSpeciesId(null);
            obs.setReportedCommonName(null);
            obs.setReportedScientificName(null);
            obs.setCategoryId(null);
        } else if (req.speciesId() != null) {
            obs.setIdentificationStatus("IDENTIFIED");
        } else if (obs.getIdentificationStatus() == null) {
            obs.setIdentificationStatus("IDENTIFIED");
        }
        obs.setProvinceName(regionService.resolveName(obs.getProvinceCode()));
        obs.setCityName(regionService.resolveName(obs.getCityCode()));
        obs.setDistrictName(regionService.resolveName(obs.getDistrictCode()));
        if (obs.getSpeciesId() != null) {
            PlantSpecies species = plantSpeciesMapper.selectById(obs.getSpeciesId());
            if (species != null) {
                obs.setCategoryId(species.getCategoryId());
                if (!StringUtils.hasText(obs.getReportedCommonName())) {
                    obs.setReportedCommonName(species.getCommonName());
                }
            }
        } else {
            obs.setCategoryId(null);
        }
    }

    private void validateBeforeSubmit(PlantObservation obs) {
        if (photoService.countPhotos(obs.getId()) < 1) {
            throw new BusinessException("至少上传1张植物照片才能提交");
        }
        if (!StringUtils.hasText(obs.getProvinceCode())) {
            throw new BusinessException("请选择省份后才能提交");
        }
        boolean pendingIdentification = "PENDING".equals(obs.getIdentificationStatus());
        if (!pendingIdentification && obs.getSpeciesId() == null && !StringUtils.hasText(obs.getReportedCommonName())) {
            throw new BusinessException("请选择植物或填写植物名称后才能提交");
        }
        if (obs.getObservedAt() == null) {
            throw new BusinessException("请填写观察时间后才能提交");
        }
        List<String> missing = missingRequiredFields(obs);
        if (!missing.isEmpty()) {
            throw new BusinessException("请填写必填描述项: " + String.join("、", missing));
        }
    }

    private List<String> missingRequiredFields(PlantObservation obs) {
        List<PlantFieldDefinition> defs = fieldDefinitionMapper.selectList(Wrappers.<PlantFieldDefinition>lambdaQuery()
                .eq(PlantFieldDefinition::getRequired, 1)
                .eq(PlantFieldDefinition::getEnabled, 1)
                .and(w -> w.eq(PlantFieldDefinition::getScopeType, "GLOBAL")
                        .or(o -> o.eq(PlantFieldDefinition::getScopeType, "CATEGORY")
                                .eq(obs.getCategoryId() != null, PlantFieldDefinition::getScopeId, obs.getCategoryId()))));
        if (defs.isEmpty()) {
            return new ArrayList<>();
        }
        List<PlantFieldValue> values = fieldValueMapper.selectList(Wrappers.<PlantFieldValue>lambdaQuery()
                .eq(PlantFieldValue::getObservationId, obs.getId()));
        Map<Long, PlantFieldValue> valueMap = values.stream()
                .collect(Collectors.toMap(PlantFieldValue::getFieldId, Function.identity()));
        List<String> missing = new ArrayList<>();
        for (PlantFieldDefinition def : defs) {
            PlantFieldValue v = valueMap.get(def.getId());
            if (v == null || (!StringUtils.hasText(v.getValueText()) && !StringUtils.hasText(v.getValueJson()))) {
                missing.add(def.getFieldLabel());
            }
        }
        return missing;
    }

    public void replaceFieldValues(Long observationId, List<FieldValueEntry> entries) {
        fieldValueMapper.delete(Wrappers.<PlantFieldValue>lambdaQuery()
                .eq(PlantFieldValue::getObservationId, observationId));
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (FieldValueEntry entry : entries) {
            if (entry.fieldId() == null) {
                continue;
            }
            PlantFieldDefinition def = fieldDefinitionMapper.selectById(entry.fieldId());
            if (def == null) {
                throw new BusinessException("描述项不存在: " + entry.fieldId());
            }
            PlantFieldValue value = new PlantFieldValue();
            value.setObservationId(observationId);
            value.setFieldId(entry.fieldId());
            value.setFieldVersion(def.getVersion() == null ? 1 : def.getVersion());
            value.setValueText(trim(entry.valueText()));
            value.setValueJson(trim(entry.valueJson()));
            fieldValueMapper.insert(value);
        }
    }

    private String trim(String s) {
        return s == null ? null : (s.isBlank() ? null : s.trim());
    }
}