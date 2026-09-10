package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.plant.dto.CategorySaveRequest;
import com.jingxuan.plant.dto.SpeciesSaveRequest;
import com.jingxuan.plant.entity.PlantCategory;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.mapper.PlantCategoryMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 物种库与类别：公开搜索/详情 + 管理员维护。 */
@Service
@RequiredArgsConstructor
public class PlantSpeciesService {

    private final PlantSpeciesMapper plantSpeciesMapper;
    private final PlantCategoryMapper plantCategoryMapper;

    // ---------- 公开 ----------

    public PageResult<PlantSpecies> search(String keyword, Long categoryId, int page, int size) {
        return PageUtil.query(page, size, plantSpeciesMapper, w -> w
                .eq(PlantSpecies::getEnabled, 1)
                .eq(categoryId != null, PlantSpecies::getCategoryId, categoryId)
                .and(StringUtils.hasText(keyword), ww -> ww
                        .like(PlantSpecies::getCommonName, keyword)
                        .or().like(PlantSpecies::getScientificName, keyword)
                        .or().like(PlantSpecies::getAliasNames, keyword))
                .orderByAsc(PlantSpecies::getCommonName));
    }

    public PlantSpecies getById(Long id) {
        return plantSpeciesMapper.selectById(id);
    }

    public List<PlantCategory> categoryList() {
        return plantCategoryMapper.selectList(Wrappers.<PlantCategory>lambdaQuery()
                .orderByAsc(PlantCategory::getSortOrder));
    }

    public Map<Long, PlantCategory> categoryMap() {
        return categoryList().stream()
                .collect(Collectors.toMap(PlantCategory::getId, Function.identity()));
    }

    public Map<Long, PlantSpecies> speciesMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return plantSpeciesMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PlantSpecies::getId, Function.identity()));
    }

    // ---------- 管理员 ----------

    @Transactional(rollbackFor = Exception.class)
    public PlantCategory createCategory(CategorySaveRequest req) {
        PlantCategory entity = new PlantCategory();
        entity.setName(req.name());
        entity.setCode(req.code());
        entity.setDescription(req.description());
        entity.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        entity.setEnabled(req.enabled() == null ? 1 : req.enabled());
        plantCategoryMapper.insert(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantCategory updateCategory(Long id, CategorySaveRequest req) {
        PlantCategory entity = plantCategoryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("植物类别不存在");
        }
        entity.setName(req.name());
        entity.setCode(req.code());
        entity.setDescription(req.description());
        if (req.sortOrder() != null) {
            entity.setSortOrder(req.sortOrder());
        }
        if (req.enabled() != null) {
            entity.setEnabled(req.enabled());
        }
        plantCategoryMapper.updateById(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantSpecies createSpecies(SpeciesSaveRequest req, Long operatorId) {
        PlantSpecies entity = new PlantSpecies();
        copySpeciesFields(entity, req);
        entity.setCreatedBy(operatorId);
        plantSpeciesMapper.insert(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlantSpecies updateSpecies(Long id, SpeciesSaveRequest req) {
        PlantSpecies entity = plantSpeciesMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("植物物种不存在");
        }
        copySpeciesFields(entity, req);
        plantSpeciesMapper.updateById(entity);
        return entity;
    }

    private void copySpeciesFields(PlantSpecies entity, SpeciesSaveRequest req) {
        entity.setCommonName(req.commonName());
        entity.setScientificName(req.scientificName());
        entity.setAliasNames(req.aliasNames());
        entity.setFamilyName(req.familyName());
        entity.setGenusName(req.genusName());
        entity.setSpeciesName(req.speciesName());
        entity.setCategoryId(req.categoryId());
        entity.setDescription(req.description());
        entity.setCoverUrl(req.coverUrl());
        entity.setSource(req.source());
        entity.setEnabled(req.enabled() == null ? 1 : req.enabled());
    }
}
