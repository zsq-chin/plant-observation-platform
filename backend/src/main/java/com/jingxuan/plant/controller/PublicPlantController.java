package com.jingxuan.plant.controller;

import com.jingxuan.common.PageResult;
import com.jingxuan.common.Result;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.entity.SysRegion;
import com.jingxuan.plant.service.PlantGalleryService;
import com.jingxuan.plant.service.PlantMapService;
import com.jingxuan.plant.service.PlantRegionService;
import com.jingxuan.plant.service.PlantSpeciesService;
import com.jingxuan.plant.vo.GalleryItemVO;
import com.jingxuan.plant.vo.HomeVO;
import com.jingxuan.plant.vo.MapChinaStatVO;
import com.jingxuan.plant.vo.ObservationPublicDetailVO;
import com.jingxuan.plant.vo.ProvinceSpeciesStatVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 植物平台公开接口（无需登录，只暴露审核通过且公开的数据）。 */
@Tag(name = "植物平台-公开端")
@RestController
@RequestMapping("/api/public/plant")
@RequiredArgsConstructor
public class PublicPlantController {

    private final PlantRegionService regionService;
    private final PlantSpeciesService speciesService;
    private final PlantGalleryService galleryService;
    private final PlantMapService mapService;
    private final com.jingxuan.plant.service.PlantCommunityService communityService;
    private final com.jingxuan.plant.service.ProvinceVisualizationService provinceVisualizationService;
    private final com.jingxuan.plant.service.PlantSearchService plantSearchService;

    @Operation(summary = "全局搜索（物种+公开观察）")
    @GetMapping("/search")
    public Result<com.jingxuan.plant.vo.PlantSearchResultVO> search(@RequestParam(required = false) String keyword) {
        return Result.ok(plantSearchService.search(keyword));
    }

    @Operation(summary = "省份列表")
    @GetMapping("/regions/provinces")
    public Result<List<SysRegion>> provinces() {
        return Result.ok(regionService.listProvinces());
    }

    @Operation(summary = "按上级区划查下级（城市/区县）")
    @GetMapping("/regions/{parentCode}/children")
    public Result<List<SysRegion>> children(@PathVariable String parentCode) {
        return Result.ok(regionService.listChildren(parentCode));
    }

    @Operation(summary = "植物类别列表（公开）")
    @GetMapping("/categories")
    public Result<List<com.jingxuan.plant.entity.PlantCategory>> categories() {
        return Result.ok(speciesService.categoryList().stream()
                .filter(c -> Integer.valueOf(1).equals(c.getEnabled())).toList());
    }

    @Operation(summary = "物种搜索")
    @GetMapping("/species/search")
    public Result<PageResult<PlantSpecies>> searchSpecies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(speciesService.search(keyword, categoryId, page, size));
    }

    @Operation(summary = "物种详情")
    @GetMapping("/species/{id}")
    public Result<PlantSpecies> speciesDetail(@PathVariable Long id) {
        return Result.ok(speciesService.getById(id));
    }

    @Operation(summary = "植物观察展廊（分页）")
    @GetMapping("/gallery")
    public Result<PageResult<GalleryItemVO>> gallery(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String sort) {
        return Result.ok(galleryService.gallery(page, size, keyword, provinceCode, categoryId, classId, year, featured, sort));
    }

    @Operation(summary = "首页聚合数据")
    @GetMapping("/home")
    public Result<HomeVO> home() {
        return Result.ok(galleryService.home());
    }

    @Operation(summary = "公开观察详情")
    @GetMapping("/observations/{id}")
    public Result<ObservationPublicDetailVO> observationDetail(@PathVariable Long id) {
        return Result.ok(galleryService.publicDetail(id));
    }

    @Operation(summary = "观察记录评论列表（公开）")
    @GetMapping("/observations/{id}/comments")
    public Result<List<com.jingxuan.plant.vo.CommentVO>> comments(@PathVariable Long id) {
        return Result.ok(communityService.listComments(id));
    }

    @Operation(summary = "全国地图省份聚合统计")
    @GetMapping("/map/china")
    public Result<List<MapChinaStatVO>> mapChina(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long speciesId,
            @RequestParam(required = false) Integer year) {
        return Result.ok(mapService.china(classId, categoryId, speciesId, year));
    }

    @Operation(summary = "省份植物目录")
    @GetMapping("/map/provinces/{provinceCode}/species")
    public Result<List<ProvinceSpeciesStatVO>> provinceSpecies(
            @PathVariable String provinceCode,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String keyword) {
        return Result.ok(mapService.provinceSpecies(provinceCode, classId, year, keyword));
    }

    @Operation(summary = "省内行政区可视化聚合（3D 地图）")
    @GetMapping("/map/provinces/{provinceCode}/visualization")
    public Result<com.jingxuan.plant.vo.ProvinceVisualizationVO> provinceVisualization(
            @PathVariable String provinceCode,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String keyword) {
        return Result.ok(provinceVisualizationService.visualize(
                provinceCode, categoryId, classId, year, keyword));
    }

    @Operation(summary = "某物种的观察记录（可按省份筛选）")
    @GetMapping("/map/species/{speciesId}/observations")
    public Result<PageResult<GalleryItemVO>> speciesObservations(
            @PathVariable Long speciesId,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(mapService.speciesObservations(speciesId, provinceCode, page, size));
    }

    @Operation(summary = "全国地图精选作品（每省最多 1 条，默认 8 条）")
    @GetMapping("/map/featured-works")
    public Result<List<GalleryItemVO>> featuredWorks(@RequestParam(defaultValue = "8") int size) {
        return Result.ok(mapService.featuredWorks(size));
    }

    @Operation(summary = "某省学生作品（点击省份后加载）")
    @GetMapping("/map/provinces/{provinceCode}/works")
    public Result<PageResult<GalleryItemVO>> provinceWorks(
            @PathVariable String provinceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return Result.ok(mapService.provinceWorks(provinceCode, page, size));
    }
}