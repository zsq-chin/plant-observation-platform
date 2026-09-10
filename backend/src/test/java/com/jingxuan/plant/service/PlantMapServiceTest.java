package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.mapper.PlantCategoryMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import com.jingxuan.plant.mapper.PlantRatingMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.vo.GalleryItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 全国地图精选作品规则（V4 下一步开发计划 §4.1）：只取公开数据且同一省份最多一条。 */
class PlantMapServiceTest {

    private final PlantObservationMapper observationMapper = mock(PlantObservationMapper.class);
    private final PlantPhotoMapper photoMapper = mock(PlantPhotoMapper.class);
    private final PlantSpeciesMapper speciesMapper = mock(PlantSpeciesMapper.class);
    private final PlantCategoryMapper categoryMapper = mock(PlantCategoryMapper.class);
    private final PlantRatingMapper ratingMapper = mock(PlantRatingMapper.class);
    private final PlantGalleryService galleryService = mock(PlantGalleryService.class);
    private final PlantRegionService regionService = mock(PlantRegionService.class);

    private final PlantMapService service = new PlantMapService(observationMapper, photoMapper, speciesMapper,
            categoryMapper, ratingMapper, galleryService, regionService);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PlantObservation.class);
    }

    private static PlantObservation obs(long id, String provinceCode, boolean featured) {
        PlantObservation row = new PlantObservation();
        row.setId(id);
        row.setStatus(PlantStatuses.APPROVED);
        row.setIsPublic(1);
        row.setProvinceCode(provinceCode);
        row.setFeatured(featured ? 1 : 0);
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<PlantObservation> capturePicked(int size) {
        when(observationMapper.selectList(any())).thenReturn(rows);
        when(galleryService.toItems(any())).thenReturn(new ArrayList<GalleryItemVO>());
        service.featuredWorks(size);
        ArgumentCaptor<List<PlantObservation>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(galleryService).toItems(captor.capture());
        return captor.getValue();
    }

    private List<PlantObservation> rows = List.of();

    @Test
    void keepsOnlyOneFeaturedPerProvince() {
        rows = List.of(
                obs(1L, "330000", true),
                obs(2L, "330000", true),
                obs(3L, "510000", true),
                obs(4L, "320000", true));
        List<PlantObservation> picked = capturePicked(8);
        assertEquals(3, picked.size(), "同一省份只保留一条");
        Set<String> provinces = new HashSet<>();
        for (PlantObservation row : picked) {
            assertTrue(provinces.add(row.getProvinceCode()), "省份不应重复: " + row.getProvinceCode());
        }
    }

    @Test
    void fillsWithLatestWhenFeaturedNotEnough() {
        rows = List.of(
                obs(1L, "330000", true),
                obs(2L, "510000", false),
                obs(3L, "320000", false));
        List<PlantObservation> picked = capturePicked(3);
        assertEquals(3, picked.size(), "精选不足时用最新公开记录补齐");
    }

    @Test
    void respectsSizeLimit() {
        rows = List.of(
                obs(1L, "330000", true),
                obs(2L, "510000", true),
                obs(3L, "320000", true));
        assertEquals(1, capturePicked(1).size());
    }
}
