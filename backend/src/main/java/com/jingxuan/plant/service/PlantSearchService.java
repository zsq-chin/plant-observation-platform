package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.vo.PlantSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 全局搜索（V4 §93-94）：物种 + 公开观察。 */
@Service
@RequiredArgsConstructor
public class PlantSearchService {

    private static final int SPECIES_LIMIT = 8;
    private static final int OBSERVATION_LIMIT = 12;

    private final PlantSpeciesMapper speciesMapper;
    private final PlantObservationMapper observationMapper;
    private final PlantGalleryService galleryService;

    public PlantSearchResultVO search(String keyword) {
        PlantSearchResultVO vo = new PlantSearchResultVO();
        if (!StringUtils.hasText(keyword)) {
            vo.setSpecies(List.of());
            vo.setObservations(List.of());
            return vo;
        }
        String kw = keyword.trim();
        List<Long> speciesIds = speciesMapper.selectList(Wrappers.<PlantSpecies>lambdaQuery()
                        .select(PlantSpecies::getId)
                        .like(PlantSpecies::getCommonName, kw)
                        .or().like(PlantSpecies::getScientificName, kw)
                        .or().like(PlantSpecies::getAliasNames, kw)
                        .last("LIMIT " + SPECIES_LIMIT))
                .stream().map(PlantSpecies::getId).toList();
        List<PlantSpecies> species = speciesIds.isEmpty() ? List.of()
                : speciesMapper.selectBatchIds(speciesIds);
        vo.setSpecies(species);

        List<PlantObservation> rows = new ArrayList<>();
        var wrapper = Wrappers.<PlantObservation>lambdaQuery()
                .eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
                .eq(PlantObservation::getIsPublic, 1)
                .orderByDesc(PlantObservation::getPublishedAt);
        if (speciesIds.isEmpty()) {
            wrapper.like(PlantObservation::getReportedCommonName, kw)
                    .or().like(PlantObservation::getReportedScientificName, kw);
        } else {
            wrapper.and(w -> w.in(PlantObservation::getSpeciesId, speciesIds)
                    .or().like(PlantObservation::getReportedCommonName, kw));
        }
        wrapper.last("LIMIT " + OBSERVATION_LIMIT);
        rows = observationMapper.selectList(wrapper);
        vo.setObservations(galleryService.toItems(rows));
        return vo;
    }
}
