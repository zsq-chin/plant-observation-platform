package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import org.springframework.beans.factory.annotation.Value;
import com.jingxuan.entity.SysUser;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantCategory;
import com.jingxuan.plant.entity.PlantFieldDefinition;
import com.jingxuan.plant.entity.PlantFieldValue;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.entity.PlantRating;
import com.jingxuan.plant.entity.PlantReview;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.mapper.PlantCategoryMapper;
import com.jingxuan.plant.mapper.PlantCommentMapper;
import com.jingxuan.plant.mapper.PlantFieldDefinitionMapper;
import com.jingxuan.plant.mapper.PlantFieldValueMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import com.jingxuan.plant.mapper.PlantRatingMapper;
import com.jingxuan.plant.mapper.PlantReviewMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.vo.GalleryItemVO;
import com.jingxuan.plant.vo.HomeVO;
import com.jingxuan.plant.vo.ObservationPublicDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 公开展廊与首页聚合：只返回 APPROVED 且 is_public=1 的数据。 */
@Service
@RequiredArgsConstructor
public class PlantGalleryService {

    @Value("${plant.privacy.show-real-name:true}")
    private boolean showRealName;

    private static final int HOME_FEATURED_SIZE = 6;
    private static final int HOME_LATEST_SIZE = 6;

    private final PlantObservationMapper observationMapper;
    private final PlantPhotoMapper photoMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final PlantCategoryMapper categoryMapper;
    private final PlantRatingMapper ratingMapper;
    private final PlantCommentMapper commentMapper;
    private final PlantReviewMapper reviewMapper;
    private final PlantFieldDefinitionMapper fieldDefinitionMapper;
    private final PlantFieldValueMapper fieldValueMapper;
    private final SysUserMapper sysUserMapper;
    private final PlantRegionService regionService;
    private final PlantCommunityService communityService;

    public PageResult<GalleryItemVO> gallery(int page, int size, String keyword, String provinceCode,
                                             Long categoryId, Long classId, Integer year, Boolean featured, String sort) {
        List<Long> speciesIds = null;
        if (StringUtils.hasText(keyword)) {
            speciesIds = speciesMapper.selectList(Wrappers.<PlantSpecies>lambdaQuery()
                    .select(PlantSpecies::getId)
                    .like(PlantSpecies::getCommonName, keyword)
                    .or().like(PlantSpecies::getScientificName, keyword)
                    .or().like(PlantSpecies::getAliasNames, keyword))
                    .stream().map(PlantSpecies::getId).toList();
        }
        String finalKeyword = keyword;
        List<Long> finalSpeciesIds = speciesIds;
        String sortType = sort == null ? "featured" : sort;
        PageResult<PlantObservation> pageResult = PageUtil.query(page, size, observationMapper, w -> {
            w.eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
             .eq(PlantObservation::getIsPublic, 1)
             .eq(StringUtils.hasText(provinceCode), PlantObservation::getProvinceCode, provinceCode)
             .eq(categoryId != null, PlantObservation::getCategoryId, categoryId)
             .eq(classId != null, PlantObservation::getClassId, classId)
             .eq(featured != null, PlantObservation::getFeatured, Boolean.TRUE.equals(featured) ? 1 : 0);
            if (year != null) {
                w.apply("YEAR(observed_at) = {0}", year);
            }
            if (StringUtils.hasText(finalKeyword)) {
                w.and(ww -> {
                    if (finalSpeciesIds != null && !finalSpeciesIds.isEmpty()) {
                        ww.in(PlantObservation::getSpeciesId, finalSpeciesIds).or();
                    }
                    ww.like(PlantObservation::getReportedCommonName, finalKeyword)
                      .or().like(PlantObservation::getReportedScientificName, finalKeyword);
                });
            }
            switch (sortType) {
                case "latest" -> w.orderByDesc(PlantObservation::getPublishedAt).orderByDesc(PlantObservation::getId);
                case "view" -> w.orderByDesc(PlantObservation::getViewCount);
                default -> w.orderByDesc(PlantObservation::getFeatured).orderByDesc(PlantObservation::getPublishedAt);
            }
        });
        return new PageResult<>(toItems(pageResult.getRecords()), pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize());
    }

    public HomeVO home() {
        HomeVO vo = new HomeVO();
        HomeVO.Statistics statistics = new HomeVO.Statistics();
        statistics.setSpeciesCount(speciesMapper.selectCount(Wrappers.<PlantSpecies>lambdaQuery()
                .eq(PlantSpecies::getEnabled, 1)));
        statistics.setCategoryCount(categoryMapper.selectCount(Wrappers.<PlantCategory>lambdaQuery()
                .eq(PlantCategory::getEnabled, 1)));
        List<PlantObservation> approved = observationMapper.selectList(Wrappers.<PlantObservation>lambdaQuery()
                .select(PlantObservation::getSubmitterId, PlantObservation::getProvinceCode, PlantObservation::getSpeciesId)
                .eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
                .eq(PlantObservation::getIsPublic, 1));
        statistics.setObservationCount(approved.size());
        statistics.setStudentCount(approved.stream().map(PlantObservation::getSubmitterId).filter(Objects::nonNull).distinct().count());
        statistics.setProvinceCount(approved.stream().map(PlantObservation::getProvinceCode).filter(StringUtils::hasText).distinct().count());
        vo.setStatistics(statistics);
        vo.setFeaturedObservations(gallery(1, HOME_FEATURED_SIZE, null, null, null, null, null, null, "featured").getRecords());
        vo.setLatestObservations(gallery(1, HOME_LATEST_SIZE, null, null, null, null, null, null, "latest").getRecords());
        vo.setTeacherComments(communityService.latestPinnedTeacherComments(5));
        return vo;
    }

    public ObservationPublicDetailVO publicDetail(Long observationId) {
        PlantObservation obs = observationMapper.selectById(observationId);
        if (obs == null || !PlantStatuses.APPROVED.equals(obs.getStatus()) || !Integer.valueOf(1).equals(obs.getIsPublic())) {
            throw new BusinessException("该观察记录不存在或未公开");
        }
        observationMapper.update(null, Wrappers.<PlantObservation>lambdaUpdate()
                .eq(PlantObservation::getId, observationId)
                .setSql("view_count = view_count + 1"));
        return assembleDetail(obs);
    }

    /** 组装详情（教师端可传入任意状态记录复用）。 */
    public ObservationPublicDetailVO assembleDetail(PlantObservation obs) {
        ObservationPublicDetailVO vo = new ObservationPublicDetailVO();
        vo.setObservationId(obs.getId());
        vo.setSpeciesId(obs.getSpeciesId());
        PlantSpecies species = obs.getSpeciesId() != null ? speciesMapper.selectById(obs.getSpeciesId()) : null;
        vo.setCommonName(species != null ? species.getCommonName() : null);
        vo.setScientificName(species != null ? species.getScientificName() : null);
        vo.setCategoryId(obs.getCategoryId());
        vo.setCategoryName(loadCategoryName(obs.getCategoryId()));
        vo.setFamilyName(species != null ? species.getFamilyName() : null);
        vo.setGenusName(species != null ? species.getGenusName() : null);
        vo.setReportedCommonName(obs.getReportedCommonName());
        vo.setReportedScientificName(obs.getReportedScientificName());
        vo.setPhotos(loadPhotos(obs.getId()));
        vo.setProvinceName(obs.getProvinceName());
        vo.setCityName(obs.getCityName());
        vo.setDistrictName(obs.getDistrictName());
        vo.setLocationText(obs.getLocationText());
        vo.setSubmitterName(loadUserName(obs.getSubmitterId()));
        vo.setClassName(obs.getClassNameSnapshot());
        vo.setObservedAt(obs.getObservedAt());
        vo.setDescription(obs.getDescription());
        vo.setFeatured(Integer.valueOf(1).equals(obs.getFeatured()));
        vo.setPublishedAt(obs.getPublishedAt());
        vo.setViewCount(obs.getViewCount());
        RatingSummary rating = ratingSummary(obs.getId());
        vo.setAverageRating(rating.avg);
        vo.setRatingCount(rating.count);
        vo.setCommentCount(commentMapper.selectCount(Wrappers.<com.jingxuan.plant.entity.PlantComment>lambdaQuery()
                .eq(com.jingxuan.plant.entity.PlantComment::getObservationId, obs.getId())
                .eq(com.jingxuan.plant.entity.PlantComment::getStatus, "NORMAL")));
        vo.setFieldValues(loadFieldValues(obs.getId()));
        List<PlantReview> reviews = reviewMapper.selectList(Wrappers.<PlantReview>lambdaQuery()
                .eq(PlantReview::getObservationId, obs.getId())
                .orderByDesc(PlantReview::getCreateTime)
                .last("LIMIT 1"));
        if (!reviews.isEmpty()) {
            vo.setReviewComment(reviews.get(0).getComment());
        }
        vo.setIdentificationStatus(obs.getIdentificationStatus() == null ? "IDENTIFIED" : obs.getIdentificationStatus());
        if (!PlantStatuses.APPROVED.equals(obs.getStatus())) {
            vo.setQualityWarnings(qualityWarningsOf(obs));
        }
        return vo;
    }

    /** 数据质量提示（V4 §70）：供教师审核参考，不自动拦截。 */
    private List<String> qualityWarningsOf(PlantObservation obs) {
        List<String> warnings = new ArrayList<>();
        if (photoMapper.selectCount(Wrappers.<PlantPhoto>lambdaQuery()
                .eq(PlantPhoto::getObservationId, obs.getId())) < 1) {
            warnings.add("缺少照片");
        }
        if (!StringUtils.hasText(obs.getProvinceCode())) {
            warnings.add("缺少省份");
        }
        if (!"PENDING".equals(obs.getIdentificationStatus())
                && obs.getSpeciesId() == null
                && !StringUtils.hasText(obs.getReportedCommonName())) {
            warnings.add("未选择物种也未标记待鉴定");
        }
        if (obs.getObservedAt() != null && obs.getObservedAt().isAfter(LocalDateTime.now())) {
            warnings.add("观察时间晚于当前时间");
        }
        if (obs.getSubmitterId() != null && obs.getSpeciesId() != null && obs.getProvinceCode() != null) {
            long similar = observationMapper.selectCount(Wrappers.<PlantObservation>lambdaQuery()
                    .eq(PlantObservation::getSubmitterId, obs.getSubmitterId())
                    .eq(PlantObservation::getSpeciesId, obs.getSpeciesId())
                    .eq(PlantObservation::getProvinceCode, obs.getProvinceCode())
                    .eq(PlantObservation::getStatus, PlantStatuses.APPROVED)
                    .ne(obs.getId() != null, PlantObservation::getId, obs.getId())
                    .last("LIMIT 1"));
            if (similar > 0) {
                warnings.add("该学生该物种在相同省份可能重复提交");
            }
        }
        return warnings;
    }

    // ---------- 内部组装 ----------

    public List<GalleryItemVO> toItems(List<PlantObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        List<Long> obsIds = observations.stream().map(PlantObservation::getId).toList();
        List<Long> speciesIds = observations.stream().map(PlantObservation::getSpeciesId).filter(Objects::nonNull).distinct().toList();
        Map<Long, PlantSpecies> speciesMap = speciesIds.isEmpty() ? Map.of()
                : speciesMapper.selectBatchIds(speciesIds).stream().collect(Collectors.toMap(PlantSpecies::getId, Function.identity()));
        List<Long> userIds = observations.stream().map(PlantObservation::getSubmitterId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNames.put(user.getId(), com.jingxuan.plant.PlantPrivacy.displayName(user.getRealName(), showRealName));
            }
        }
        Map<Long, PlantPhoto> covers = loadCovers(obsIds);
        Map<Long, Double> ratingAvg = ratingAvgMap(obsIds);
        Map<Long, Long> ratingCountMap = ratingCountMap(obsIds);
        Map<Long, Long> commentCountMap = commentCountMap(obsIds);
        return observations.stream().map(obs -> {
            GalleryItemVO item = new GalleryItemVO();
            item.setObservationId(obs.getId());
            item.setSpeciesId(obs.getSpeciesId());
            PlantSpecies species = obs.getSpeciesId() != null ? speciesMap.get(obs.getSpeciesId()) : null;
            item.setCommonName(species != null ? species.getCommonName() : null);
            item.setScientificName(species != null ? species.getScientificName() : null);
            item.setReportedCommonName(obs.getReportedCommonName());
            item.setCategoryId(obs.getCategoryId());
            item.setCategoryName(loadCategoryName(obs.getCategoryId()));
            item.setFamilyName(species != null ? species.getFamilyName() : null);
            PlantPhoto cover = covers.get(obs.getId());
            item.setCoverUrl(cover != null ? cover.getThumbnailUrl() != null ? cover.getThumbnailUrl() : cover.getFileUrl() : null);
            item.setProvinceCode(obs.getProvinceCode());
            item.setProvinceName(obs.getProvinceName());
            item.setCityName(obs.getCityName());
            item.setSubmitterName(userNames.get(obs.getSubmitterId()));
            item.setClassName(obs.getClassNameSnapshot());
            item.setObservedAt(obs.getObservedAt());
            item.setDescription(obs.getDescription());
            item.setFeatured(Integer.valueOf(1).equals(obs.getFeatured()));
            item.setAverageRating(ratingAvg.get(obs.getId()));
            item.setRatingCount(ratingCountMap.getOrDefault(obs.getId(), 0L));
            item.setCommentCount(commentCountMap.getOrDefault(obs.getId(), 0L));
            item.setViewCount(obs.getViewCount() == null ? 0 : obs.getViewCount());
            item.setClassId(obs.getClassId());
            return item;
        }).toList();
    }

    private Map<Long, PlantPhoto> loadCovers(List<Long> obsIds) {
        Map<Long, PlantPhoto> covers = new LinkedHashMap<>();
        List<PlantPhoto> photos = photoMapper.selectList(Wrappers.<PlantPhoto>lambdaQuery()
                .in(PlantPhoto::getObservationId, obsIds)
                .orderByDesc(PlantPhoto::getIsCover)
                .orderByAsc(PlantPhoto::getSortOrder)
                .orderByAsc(PlantPhoto::getId));
        for (PlantPhoto photo : photos) {
            covers.putIfAbsent(photo.getObservationId(), photo);
        }
        return covers;
    }

    private List<ObservationPublicDetailVO.PhotoVO> loadPhotos(Long observationId) {
        List<PlantPhoto> photos = photoMapper.selectList(Wrappers.<PlantPhoto>lambdaQuery()
                .eq(PlantPhoto::getObservationId, observationId)
                .orderByAsc(PlantPhoto::getSortOrder)
                .orderByAsc(PlantPhoto::getId));
        return photos.stream().map(p -> {
            ObservationPublicDetailVO.PhotoVO vo = new ObservationPublicDetailVO.PhotoVO();
            vo.setPhotoId(p.getId());
            vo.setFileUrl(p.getFileUrl());
            vo.setThumbnailUrl(p.getThumbnailUrl());
            vo.setOrganType(p.getOrganType());
            vo.setIsCover(Integer.valueOf(1).equals(p.getIsCover()));
            return vo;
        }).toList();
    }

    private List<Map<String, Object>> loadFieldValues(Long observationId) {
        List<PlantFieldValue> values = fieldValueMapper.selectList(Wrappers.<PlantFieldValue>lambdaQuery()
                .eq(PlantFieldValue::getObservationId, observationId));
        if (values.isEmpty()) {
            return List.of();
        }
        List<Long> fieldIds = values.stream().map(PlantFieldValue::getFieldId).toList();
        Map<Long, PlantFieldDefinition> defs = fieldDefinitionMapper.selectBatchIds(fieldIds).stream()
                .collect(Collectors.toMap(PlantFieldDefinition::getId, Function.identity()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlantFieldValue value : values) {
            PlantFieldDefinition def = defs.get(value.getFieldId());
            if (def == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fieldCode", def.getFieldCode());
            row.put("fieldLabel", def.getFieldLabel());
            row.put("fieldType", def.getFieldType());
            row.put("valueText", value.getValueText());
            result.add(row);
        }
        return result;
    }

    private Map<Long, Double> ratingAvgMap(List<Long> obsIds) {
        Map<Long, Double> map = new HashMap<>();
        List<PlantRating> ratings = ratingMapper.selectList(Wrappers.<PlantRating>lambdaQuery()
                .in(PlantRating::getObservationId, obsIds));
        Map<Long, List<Integer>> grouped = ratings.stream().collect(Collectors.groupingBy(
                PlantRating::getObservationId, Collectors.mapping(PlantRating::getScore, Collectors.toList())));
        grouped.forEach((id, scores) -> map.put(id, scores.stream().mapToInt(Integer::intValue).average().orElse(0)));
        return map;
    }

    private Map<Long, Long> ratingCountMap(List<Long> obsIds) {
        Map<Long, Long> map = new HashMap<>();
        ratingMapper.selectList(Wrappers.<PlantRating>lambdaQuery()
                .in(PlantRating::getObservationId, obsIds))
                .forEach(r -> map.merge(r.getObservationId(), 1L, Long::sum));
        return map;
    }

    private Map<Long, Long> commentCountMap(List<Long> obsIds) {
        Map<Long, Long> map = new HashMap<>();
        commentMapper.selectList(Wrappers.<com.jingxuan.plant.entity.PlantComment>lambdaQuery()
                .in(com.jingxuan.plant.entity.PlantComment::getObservationId, obsIds)
                .eq(com.jingxuan.plant.entity.PlantComment::getStatus, "NORMAL"))
                .forEach(c -> map.merge(c.getObservationId(), 1L, Long::sum));
        return map;
    }

    private String loadCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        PlantCategory category = categoryMapper.selectById(categoryId);
        return category != null ? category.getName() : null;
    }

    private String loadUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? com.jingxuan.plant.PlantPrivacy.displayName(user.getRealName(), showRealName) : null;
    }

    private record RatingSummary(Double avg, Long count) {}

    private RatingSummary ratingSummary(Long observationId) {
        List<PlantRating> ratings = ratingMapper.selectList(Wrappers.<PlantRating>lambdaQuery()
                .eq(PlantRating::getObservationId, observationId));
        if (ratings.isEmpty()) {
            return new RatingSummary(null, 0L);
        }
        double avg = ratings.stream().mapToInt(PlantRating::getScore).average().orElse(0);
        return new RatingSummary(Math.round(avg * 10.0) / 10.0, (long) ratings.size());
    }

    public String provinceNameOf(String code) {
        return regionService.resolveName(code);
    }
}