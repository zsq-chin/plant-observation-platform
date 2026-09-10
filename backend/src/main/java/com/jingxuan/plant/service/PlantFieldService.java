package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.plant.entity.PlantFieldDefinition;
import com.jingxuan.plant.mapper.PlantFieldDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/** 教师动态描述项：配置 CRUD（停用不物理删除，保留历史值）。 */
@Service
@RequiredArgsConstructor
public class PlantFieldService {

    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "SELECT", "MULTI_SELECT", "BOOLEAN", "DATE");

    private final PlantFieldDefinitionMapper fieldDefinitionMapper;

    /** 学生端拉取（启用中，按排序）。 */
    public List<PlantFieldDefinition> enabledFields() {
        return fieldDefinitionMapper.selectList(Wrappers.<PlantFieldDefinition>lambdaQuery()
                .eq(PlantFieldDefinition::getEnabled, 1)
                .orderByAsc(PlantFieldDefinition::getSortOrder)
                .orderByAsc(PlantFieldDefinition::getId));
    }

    public List<PlantFieldDefinition> allFields() {
        return fieldDefinitionMapper.selectList(Wrappers.<PlantFieldDefinition>lambdaQuery()
                .orderByAsc(PlantFieldDefinition::getSortOrder)
                .orderByAsc(PlantFieldDefinition::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantFieldDefinition create(String fieldCode, String fieldLabel, String fieldType,
                                       String scopeType, Long scopeId, String optionsJson,
                                       Integer required, Integer sortOrder, Long operatorId) {
        validate(fieldCode, fieldLabel, fieldType);
        PlantFieldDefinition def = new PlantFieldDefinition();
        def.setFieldCode(fieldCode.trim());
        def.setFieldLabel(fieldLabel.trim());
        def.setFieldType(fieldType);
        def.setScopeType(StringUtils.hasText(scopeType) ? scopeType : "GLOBAL");
        def.setScopeId(scopeId);
        def.setOptionsJson(optionsJson);
        def.setRequired(required == null || required == 1 ? (required == null ? 0 : 1) : 0);
        def.setEnabled(1);
        def.setSortOrder(sortOrder == null ? 0 : sortOrder);
        def.setCreatedBy(operatorId);
        def.setVersion(1);
        fieldDefinitionMapper.insert(def);
        return def;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantFieldDefinition update(Long id, String fieldCode, String fieldLabel, String fieldType,
                                      String scopeType, Long scopeId, String optionsJson,
                                      Integer required, Integer sortOrder) {
        PlantFieldDefinition def = fieldDefinitionMapper.selectById(id);
        if (def == null) {
            throw new BusinessException("描述项不存在");
        }
        validate(fieldCode, fieldLabel, fieldType);
        def.setFieldCode(fieldCode.trim());
        def.setFieldLabel(fieldLabel.trim());
        def.setFieldType(fieldType);
        if (StringUtils.hasText(scopeType)) {
            def.setScopeType(scopeType);
            def.setScopeId(scopeId);
        }
        def.setOptionsJson(optionsJson);
        def.setRequired(required != null && required == 1 ? 1 : 0);
        if (sortOrder != null) {
            def.setSortOrder(sortOrder);
        }
        def.setVersion(def.getVersion() == null ? 2 : def.getVersion() + 1);
        fieldDefinitionMapper.updateById(def);
        return def;
    }

    /** 停用/启用；停用不删除数据。 */
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(Long id, boolean enabled) {
        PlantFieldDefinition def = fieldDefinitionMapper.selectById(id);
        if (def == null) {
            throw new BusinessException("描述项不存在");
        }
        def.setEnabled(enabled ? 1 : 0);
        fieldDefinitionMapper.updateById(def);
    }

    private void validate(String fieldCode, String fieldLabel, String fieldType) {
        if (!StringUtils.hasText(fieldCode)) {
            throw new BusinessException("请填写字段编码");
        }
        if (!StringUtils.hasText(fieldLabel)) {
            throw new BusinessException("请填写字段名称");
        }
        if (fieldType == null || !FIELD_TYPES.contains(fieldType)) {
            throw new BusinessException("字段类型不合法");
        }
    }
}
