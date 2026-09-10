package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.entity.PlantRating;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.entity.PlantCategory;
import com.jingxuan.plant.mapper.PlantCategoryMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import com.jingxuan.plant.mapper.PlantRatingMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.vo.GalleryItemVO;
import com.jingxuan.plant.vo.MapChinaStatVO;
import com.jingxuan.plant.vo.ProvinceSpeciesStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 全国地图聚合（只统计 APPROVED 且 is_public=1 的数据）。 */
@Service
@RequiredArgsConstructor
public class PlantMapService {

    private final PlantObservationMapper observationMapper;
    private final PlantPhotoMapper photoMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final PlantCategoryMapper categoryMapper;
    private final PlantRatingMapper ratingMapper;
    private final PlantGalleryService galleryService;
    private final PlantRegionService regionService;

    private List<PlantObservation> approvedRows(Long classId, Long categoryId, Long speciesId, Integer year, String provinceCode) {
        return observationMapper.selectList(Wrappers.<PlantObservation>lambdaQuery()
                .eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
                .eq(PlantObservation::getIsPublic, 1)
                .eq(classId != null, PlantObservation::getClassId, classId)
                .eq(categoryId != null, PlantObservation::getCategoryId, categoryId)
                .eq(speciesId != null, PlantObservation::getSpeciesId, speciesId)
                .eq(StringUtils.hasText(provinceCode), PlantObservation::getProvinceCode, provinceCode)
                .and(year != null, w -> w.apply("YEAR(observed_at) = {0}", year)));
    }

    public List<MapChinaStatVO> china(Long classId, Long categoryId, Long speciesId, Integer year) {
        List<PlantObservation> rows = approvedRows(classId, categoryId, speciesId, year, null);
        Map<String, ProvinceBucket> buckets = new LinkedHashMap<>();
        for (PlantObservation obs : rows) {
            String code = obs.getProvinceCode();
            if (!StringUtils.hasText(code)) {
                continue;
            }
            ProvinceBucket bucket = buckets.computeIfAbsent(code, k -> new ProvinceBucket(code, regionService.resolveName(code)));
            bucket.observationCount++;
            bucket.speciesSet.add(obs.getSpeciesId());
            bucket.studentSet.add(obs.getSubmitterId());
        }
        List<MapChinaStatVO> result = buckets.values().stream().map(b -> {
            MapChinaStatVO vo = new MapChinaStatVO();
            vo.setProvinceCode(b.code);
            vo.setProvinceName(b.name);
            vo.setObservationCount(b.observationCount);
            vo.setSpeciesCount((long) b.speciesSet.size());
            vo.setStudentCount((long) b.studentSet.size());
            return vo;
        }).sorted((a, b) -> Long.compare(b.getObservationCount(), a.getObservationCount())).toList();
        return result;
    }

    public List<ProvinceSpeciesStatVO> provinceSpecies(String provinceCode, Long classId, Integer year, String keyword) {
        List<PlantObservation> rows = approvedRows(classId, null, null, year, provinceCode);
        Map<Long, List<PlantObservation>> bySpecies = rows.stream()
                .filter(o -> o.getSpeciesId() != null)
                .collect(Collectors.groupingBy(PlantObservation::getSpeciesId, Collectors.toList()));
        List<Long> speciesIds = new ArrayList<>(bySpecies.keySet());
        if (speciesIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PlantSpecies> speciesMap = speciesMapper.selectBatchIds(speciesIds).stream()
                .collect(Collectors.toMap(PlantSpecies::getId, Function.identity()));
        Map<Long, String> categoryNames = new HashMap<>();
        List<PlantCategory> categories = categoryMapper.selectList(Wrappers.<PlantCategory>lambdaQuery());
        for (PlantCategory category : categories) {
            categoryNames.put(category.getId(), category.getName());
        }
        List<ProvinceSpeciesStatVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<PlantObservation>> entry : bySpecies.entrySet()) {
            PlantSpecies species = speciesMap.get(entry.getKey());
            if (species == null) {
                continue;
            }
            if (StringUtils.hasText(keyword)
                    && !containsIgnoreCase(species.getCommonName(), keyword)
                    && !containsIgnoreCase(species.getScientificName(), keyword)) {
                continue;
            }
            List<PlantObservation> obsList = entry.getValue();
            ProvinceSpeciesStatVO vo = new ProvinceSpeciesStatVO();
            vo.setSpeciesId(species.getId());
            vo.setCommonName(species.getCommonName());
            vo.setScientificName(species.getScientificName());
            vo.setCategoryName(categoryNames.get(species.getCategoryId()));
            vo.setFamilyName(species.getFamilyName());
            vo.setCoverUrl(species.getCoverUrl());
            vo.setObservationCount((long) obsList.size());
            vo.setStudentCount(obsList.stream().map(PlantObservation::getSubmitterId).filter(Objects::nonNull).distinct().count());
            vo.setAverageRating(averageRatingOf(obsList));
            result.add(vo);
        }
        result.sort((a, b) -> Long.compare(b.getObservationCount(), a.getObservationCount()));
        return result;
    }

    public PageResult<GalleryItemVO> speciesObservations(Long speciesId, String provinceCode, int page, int size) {
        PageResult<PlantObservation> pageResult = PageUtil.query(page, size, observationMapper, w -> w
                .eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
                .eq(PlantObservation::getIsPublic, 1)
                .eq(speciesId != null, PlantObservation::getSpeciesId, speciesId)
                .eq(StringUtils.hasText(provinceCode), PlantObservation::getProvinceCode, provinceCode)
                .orderByDesc(PlantObservation::getPublishedAt)
                .orderByDesc(PlantObservation::getId));
        return new PageResult<>(galleryService.toItems(pageResult.getRecords()),
                pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize());
    }

    private Double averageRatingOf(List<PlantObservation> obsList) {
        List<Long> obsIds = obsList.stream().map(PlantObservation::getId).toList();
        if (obsIds.isEmpty()) {
            return null;
        }
        double total = 0;
        long count = 0;
        for (PlantRating rating : ratingMapper.selectList(Wrappers.<PlantRating>lambdaQuery()
                .in(PlantRating::getObservationId, obsIds))) {
            total += rating.getScore();
            count++;
        }
        if (count == 0) {
            return null;
        }
        return Math.round(total / count * 10.0) / 10.0;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private static class ProvinceBucket {
        private final String code;
        private final String name;
        private long observationCount;
        private final java.util.Set<Long> speciesSet = new java.util.HashSet<>();
        private final java.util.Set<Long> studentSet = new java.util.HashSet<>();

        private ProvinceBucket(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
