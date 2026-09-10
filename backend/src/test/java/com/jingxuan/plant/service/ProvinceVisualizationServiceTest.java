package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 省内可视化聚合单测（V3 §71 ProvinceVisualizationServiceTest）。 */
class ProvinceVisualizationServiceTest {

    static {
        // 纯 Mock 环境预注册实体元数据，供 Wrappers.lambdaQuery 使用
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                PlantObservation.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                PlantPhoto.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SysRegion.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                PlantSpecies.class);
    }

    private final PlantObservationMapper observationMapper = mock(PlantObservationMapper.class);
    private final SysRegionMapper regionMapper = mock(SysRegionMapper.class);
    private final PlantSpeciesMapper speciesMapper = mock(PlantSpeciesMapper.class);
    private final PlantPhotoMapper photoMapper = mock(PlantPhotoMapper.class);

    private final ProvinceVisualizationService service = new ProvinceVisualizationService(
            observationMapper, regionMapper, speciesMapper, photoMapper);

    private PlantObservation obs(long id, long submitter, String city, long species, String status, int isPublic) {
        PlantObservation o = new PlantObservation();
        o.setId(id);
        o.setSubmitterId(submitter);
        o.setCityCode(city);
        o.setSpeciesId(species);
        o.setStatus(status);
        o.setIsPublic(isPublic);
        o.setProvinceCode("330000");
        return o;
    }

    private void stubCommon() {
        SysRegion province = new SysRegion();
        province.setRegionCode("330000");
        province.setRegionName("浙江省");
        SysRegion hangzhou = new SysRegion();
        hangzhou.setRegionCode("330100");
        hangzhou.setRegionName("杭州市");
        hangzhou.setParentCode("330000");
        hangzhou.setRegionLevel("CITY");
        hangzhou.setSortOrder(1);
        hangzhou.setCenterLng(BigDecimal.valueOf(120.15));
        hangzhou.setCenterLat(BigDecimal.valueOf(30.28));
        SysRegion ningbo = new SysRegion();
        ningbo.setRegionCode("330200");
        ningbo.setRegionName("宁波市");
        ningbo.setParentCode("330000");
        ningbo.setRegionLevel("CITY");
        ningbo.setSortOrder(2);
        ningbo.setCenterLng(BigDecimal.valueOf(121.55));
        ningbo.setCenterLat(BigDecimal.valueOf(29.87));
        when(regionMapper.selectOne(any())).thenReturn(province);
        when(regionMapper.selectList(any())).thenReturn(List.of(hangzhou, ningbo));

        PlantSpecies ginkgo = new PlantSpecies();
        ginkgo.setId(1L);
        ginkgo.setCommonName("银杏");
        PlantSpecies osmanthus = new PlantSpecies();
        osmanthus.setId(2L);
        osmanthus.setCommonName("桂花");
        osmanthus.setCoverUrl("/media/plants/original/species/osmanthus.jpg");
        when(speciesMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(ginkgo, osmanthus));

        PlantPhoto repPhoto = new PlantPhoto();
        repPhoto.setObservationId(300L);
        repPhoto.setThumbnailUrl("/media/plants/thumbnail/300/a.jpg");
        repPhoto.setIsCover(1);
        when(photoMapper.selectList(any())).thenReturn(List.of(repPhoto));
    }

    @Test
    void aggregatesOnlyApprovedPublicRowsByCityWithTopSpeciesAndCovers() {
        stubCommon();
        List<PlantObservation> rows = List.of(
                obs(100L, 1001L, "330100", 1L, PlantStatuses.APPROVED, 1),
                obs(200L, 1002L, "330100", 1L, PlantStatuses.APPROVED, 1),
                obs(250L, 1001L, "330100", 2L, PlantStatuses.APPROVED, 1),
                obs(300L, 1003L, "330200", 1L, PlantStatuses.APPROVED, 1),
                obs(400L, 1001L, "330100", 1L, PlantStatuses.DRAFT, 0));
        when(observationMapper.selectList(any())).thenReturn(rows);

        ProvinceVisualizationVO vo = service.visualize("330000", null, null, null, null);

        assertEquals("浙江省", vo.getProvince().getName());
        assertEquals(4, vo.getProvince().getObservationCount(), "草稿不应计入");
        assertEquals(2, vo.getProvince().getSpeciesCount());
        assertEquals(3, vo.getProvince().getStudentCount());

        assertEquals(2, vo.getRegions().size());
        ProvinceVisualizationVO.RegionInfo hangzhou = vo.getRegions().get(0);
        assertEquals("杭州市", hangzhou.getRegionName());
        assertEquals(3, hangzhou.getObservationCount());
        assertEquals(2, hangzhou.getSpeciesCount());
        assertEquals(2, hangzhou.getStudentCount());
        assertEquals(120.15, hangzhou.getCenterLng());
        assertEquals("银杏", hangzhou.getTopSpecies().get(0).getCommonName());
        assertEquals(2, hangzhou.getTopSpecies().get(0).getObservationCount());

        ProvinceVisualizationVO.RegionInfo ningbo = vo.getRegions().get(1);
        assertEquals(1, ningbo.getObservationCount());

        // 封面：物种1 代表观察=300 -> 缩略图；物种2 无照片 -> 物种封面
        ProvinceVisualizationVO.TopSpeciesItem ginkgoItem = vo.getTopSpecies().stream()
                .filter(i -> "银杏".equals(i.getCommonName())).findFirst().orElseThrow();
        assertEquals("/media/plants/thumbnail/300/a.jpg", ginkgoItem.getCoverUrl());
        ProvinceVisualizationVO.TopSpeciesItem osmanthusItem = vo.getTopSpecies().stream()
                .filter(i -> "桂花".equals(i.getCommonName())).findFirst().orElseThrow();
        assertEquals("/media/plants/original/species/osmanthus.jpg", osmanthusItem.getCoverUrl());
    }

    @Test
    void queryRestrictsToApprovedPublicProvinceAndOptionalFilters() {
        stubCommon();
        when(observationMapper.selectList(any())).thenReturn(List.of(
                obs(100L, 1L, "330100", 1L, PlantStatuses.APPROVED, 1)));

        service.visualize("330000", 1L, 5L, 2026, null);

        var captor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(observationMapper).selectList(captor.capture());
        LambdaQueryWrapper<?> wrapper = captor.getValue();
        wrapper.getCustomSqlSegment(); // 触发参数渲染
        var pairs = wrapper.getParamNameValuePairs().values();
        assertTrue(pairs.contains("APPROVED"), "必须限定 APPROVED");
        assertTrue(pairs.contains(1), "必须限定 is_public=1");
        assertTrue(pairs.contains("330000"), "必须限定省份");
        assertTrue(pairs.contains(1L), "categoryId 应传入");
        assertTrue(pairs.contains(5L), "classId 应传入");
        assertTrue(pairs.contains(2026), "year 应传入");
    }
}
