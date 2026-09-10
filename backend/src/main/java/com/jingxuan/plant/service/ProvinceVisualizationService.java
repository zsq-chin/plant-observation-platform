package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.entity.SysRegion;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.mapper.SysRegionMapper;
import com.jingxuan.plant.vo.ProvinceVisualizationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 省内可视化聚合（V3 §35-38）：只统计 APPROVED 且 is_public=1。
 * 行政区中心仅为展示锚点，不代表精确采集坐标。
 */
@Service
@RequiredArgsConstructor
public class ProvinceVisualizationService {

    private static final int TOP_SPECIES_PER_REGION = 3;
    private static final int TOP_SPECIES_PROVINCE = 6;

    private final PlantObservationMapper observationMapper;
    private final SysRegionMapper regionMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final PlantPhotoMapper photoMapper;

    public ProvinceVisualizationVO visualize(String provinceCode, Long categoryId, Long classId,
                                             Integer year, String keyword) {
        ProvinceVisualizationVO vo = new ProvinceVisualizationVO();
        List<PlantObservation> rows = loadApprovedRows(provinceCode, categoryId, classId, year);
        // 内存二次过滤公开审核态（与 mapper 条件一致，便于单测验证）
        rows = rows.stream()
                .filter(o -> PlantStatuses.APPROVED.equals(o.getStatus()) && Integer.valueOf(1).equals(o.getIsPublic()))
                .toList();

        Map<Long, PlantSpecies> speciesMap = loadSpeciesMap(rows, keyword);
        rows = rows.stream().filter(o -> o.getSpeciesId() == null || speciesMap.containsKey(o.getSpeciesId())).toList();

        List<SysRegion> cities = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getRegionLevel, "CITY")
                .eq(SysRegion::getParentCode, provinceCode)
                .orderByAsc(SysRegion::getSortOrder)
                .orderByAsc(SysRegion::getRegionCode));
        Map<String, SysRegion> cityMap = cities.stream()
                .collect(Collectors.toMap(SysRegion::getRegionCode, Function.identity()));

        Map<String, List<PlantObservation>> byCity = rows.stream()
                .filter(o -> o.getCityCode() != null && cityMap.containsKey(o.getCityCode()))
                .collect(Collectors.groupingBy(PlantObservation::getCityCode, LinkedHashMap::new, Collectors.toList()));

        // 每个（城市x物种）与全省每个物种的“代表观察”（最新一条），用于封面
        Map<Long, PlantObservation> speciesRep = newestPerSpecies(rows);
        List<ProvinceVisualizationVO.RegionInfo> regionInfos = new ArrayList<>();
        for (Map.Entry<String, List<PlantObservation>> entry : byCity.entrySet()) {
            SysRegion city = cityMap.get(entry.getKey());
            List<ProvinceVisualizationVO.TopSpeciesItem> top =
                    topSpecies(entry.getValue(), speciesMap, TOP_SPECIES_PER_REGION);
            ProvinceVisualizationVO.RegionInfo info = new ProvinceVisualizationVO.RegionInfo();
            info.setRegionCode(city.getRegionCode());
            info.setRegionName(city.getRegionName());
            if (city.getCenterLng() != null) info.setCenterLng(city.getCenterLng().doubleValue());
            if (city.getCenterLat() != null) info.setCenterLat(city.getCenterLat().doubleValue());
            info.setObservationCount(entry.getValue().size());
            info.setSpeciesCount(entry.getValue().stream().map(PlantObservation::getSpeciesId).filter(Objects::nonNull).distinct().count());
            info.setStudentCount(entry.getValue().stream().map(PlantObservation::getSubmitterId).filter(Objects::nonNull).distinct().count());
            info.setTopSpecies(top);
            regionInfos.add(info);
        }
        vo.setRegions(regionInfos);
        vo.setTopSpecies(topSpecies(rows, speciesMap, TOP_SPECIES_PROVINCE));

        ProvinceVisualizationVO.ProvinceInfo province = new ProvinceVisualizationVO.ProvinceInfo();
        province.setCode(provinceCode);
        province.setObservationCount(rows.size());
        province.setSpeciesCount(rows.stream().map(PlantObservation::getSpeciesId).filter(Objects::nonNull).distinct().count());
        province.setStudentCount(rows.stream().map(PlantObservation::getSubmitterId).filter(Objects::nonNull).distinct().count());
        SysRegion provinceRegion = regionMapper.selectOne(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getRegionCode, provinceCode).last("LIMIT 1"));
        province.setName(provinceRegion != null ? provinceRegion.getRegionName() : provinceCode);
        vo.setProvince(province);

        // 封面：代表观察缩略图优先，其次物种封面（PlantCoverResolver 策略，V3 §65）
        Map<Long, String> covers = new HashMap<>();
        List<Long> repIds = speciesRep.values().stream().map(PlantObservation::getId).distinct().toList();
        if (!repIds.isEmpty()) {
            for (PlantPhoto photo : photoMapper.selectList(Wrappers.<PlantPhoto>lambdaQuery()
                    .in(PlantPhoto::getObservationId, repIds)
                    .orderByDesc(PlantPhoto::getIsCover)
                    .orderByAsc(PlantPhoto::getId))) {
                String url = photo.getThumbnailUrl() != null ? photo.getThumbnailUrl() : photo.getFileUrl();
                covers.putIfAbsent(photo.getObservationId(), url);
            }
        }
        fillCovers(vo, speciesRep, covers, speciesMap);
        return vo;
    }

    private List<PlantObservation> loadApprovedRows(String provinceCode, Long categoryId, Long classId, Integer year) {
        LambdaQueryWrapper<PlantObservation> wrapper = Wrappers.<PlantObservation>lambdaQuery()
                .eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
                .eq(PlantObservation::getIsPublic, 1)
                .eq(PlantObservation::getProvinceCode, provinceCode)
                .eq(categoryId != null, PlantObservation::getCategoryId, categoryId)
                .eq(classId != null, PlantObservation::getClassId, classId)
                .and(year != null, w -> w.apply("YEAR(observed_at) = {0}", year));
        return observationMapper.selectList(wrapper);
    }

    private Map<Long, PlantSpecies> loadSpeciesMap(List<PlantObservation> rows, String keyword) {
        List<Long> ids = rows.stream().map(PlantObservation::getSpeciesId).filter(Objects::nonNull).distinct().toList();
        Map<Long, PlantSpecies> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (PlantSpecies species : speciesMapper.selectBatchIds(ids)) {
                map.put(species.getId(), species);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            map.entrySet().removeIf(e -> {
                PlantSpecies species = e.getValue();
                if (species.getCommonName() != null && species.getCommonName().toLowerCase().contains(kw)) return false;
                return species.getScientificName() != null && species.getScientificName().toLowerCase().contains(kw);
            });
        }
        return map;
    }

    private Map<Long, PlantObservation> newestPerSpecies(List<PlantObservation> rows) {
        Map<Long, PlantObservation> rep = new HashMap<>();
        for (PlantObservation obs : rows) {
            if (obs.getSpeciesId() == null) continue;
            PlantObservation current = rep.get(obs.getSpeciesId());
            if (current == null || obs.getId() > current.getId()) {
                rep.put(obs.getSpeciesId(), obs);
            }
        }
        return rep;
    }

    private List<ProvinceVisualizationVO.TopSpeciesItem> topSpecies(List<PlantObservation> rows,
                                                                    Map<Long, PlantSpecies> speciesMap,
                                                                    int limit) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (PlantObservation obs : rows) {
            if (obs.getSpeciesId() != null) {
                counts.merge(obs.getSpeciesId(), 1L, Long::sum);
            }
        }
        List<ProvinceVisualizationVO.TopSpeciesItem> result = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .forEach(entry -> {
                    PlantSpecies species = speciesMap.get(entry.getKey());
                    if (species == null) return;
                    ProvinceVisualizationVO.TopSpeciesItem item = new ProvinceVisualizationVO.TopSpeciesItem();
                    item.setSpeciesId(species.getId());
                    item.setCommonName(species.getCommonName());
                    item.setObservationCount(entry.getValue());
                    result.add(item);
                });
        return result;
    }

    private void fillCovers(ProvinceVisualizationVO vo, Map<Long, PlantObservation> speciesRep,
                            Map<Long, String> covers, Map<Long, PlantSpecies> speciesMap) {
        fillListCovers(vo.getTopSpecies(), speciesRep, covers, speciesMap);
        if (vo.getRegions() != null) {
            for (ProvinceVisualizationVO.RegionInfo region : vo.getRegions()) {
                fillListCovers(region.getTopSpecies(), speciesRep, covers, speciesMap);
            }
        }
    }

    private void fillListCovers(List<ProvinceVisualizationVO.TopSpeciesItem> items,
                                Map<Long, PlantObservation> speciesRep,
                                Map<Long, String> covers,
                                Map<Long, PlantSpecies> speciesMap) {
        if (items == null) {
            return;
        }
        for (ProvinceVisualizationVO.TopSpeciesItem item : items) {
            PlantObservation rep = speciesRep.get(item.getSpeciesId());
            String cover = rep != null ? covers.get(rep.getId()) : null;
            if (cover == null) {
                PlantSpecies species = speciesMap.get(item.getSpeciesId());
                cover = species != null ? species.getCoverUrl() : null;
            }
            item.setCoverUrl(cover);
        }
    }
}
