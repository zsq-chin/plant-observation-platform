package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import com.jingxuan.common.PageResult;
import com.jingxuan.common.PageUtil;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.modules.notification.service.NotificationService;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.dto.ReviewDecisionRequest;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.entity.PlantReview;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.entity.SysUser;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantReviewMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.vo.ReviewItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 教师审核：待审列表、通过/驳回、推荐精选、审核历史。 */
@Service
@RequiredArgsConstructor
public class PlantReviewService {

    @Value("${plant.privacy.show-real-name:true}")
    private boolean showRealName;

    private final PlantObservationMapper plantObservationMapper;
    private final PlantReviewMapper plantReviewMapper;
    private final PlantSpeciesMapper plantSpeciesMapper;
    private final SysUserMapper sysUserMapper;
    private final PlantPhotoService photoService;
    private final NotificationService notificationService;

    public PageResult<ReviewItemVO> list(String status, Long classId, Long speciesId, String provinceCode, int page, int size) {
        PageResult<PlantObservation> result = PageUtil.query(page, size, plantObservationMapper, w -> w
                .eq(StringUtils.hasText(status), PlantObservation::getStatus, status)
                .eq(classId != null, PlantObservation::getClassId, classId)
                .eq(speciesId != null, PlantObservation::getSpeciesId, speciesId)
                .eq(StringUtils.hasText(provinceCode), PlantObservation::getProvinceCode, provinceCode)
                .orderByDesc(PlantObservation::getSubmitTime)
                .orderByDesc(PlantObservation::getId));
        List<PlantObservation> records = result.getRecords();
        Map<Long, String> userNames = loadUserNames(records.stream().map(PlantObservation::getSubmitterId).toList());
        List<ReviewItemVO> items = records.stream()
                .map(obs -> toItem(obs, userNames, coverOf(obs.getId())))
                .toList();
        return PageResult.of(items, result.getTotal(), result.getPageNum(), result.getPageSize());
    }

    public PlantObservation getObservation(Long observationId) {
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        if (obs == null) {
            throw new BusinessException("观察记录不存在");
        }
        return obs;
    }

    public List<PlantReview> history(Long observationId) {
        return plantReviewMapper.selectList(Wrappers.<PlantReview>lambdaQuery()
                .eq(PlantReview::getObservationId, observationId)
                .orderByDesc(PlantReview::getCreateTime));
    }

    /** 通过：状态 -> APPROVED，公开并记录 published_at。 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long observationId, ReviewDecisionRequest req, Long reviewerId) {
        PlantObservation obs = requireSubmittable(observationId);
        if (req.bindSpeciesId() != null) {
            PlantSpecies species = plantSpeciesMapper.selectById(req.bindSpeciesId());
            if (species == null) {
                throw new BusinessException("绑定的标准物种不存在");
            }
            obs.setSpeciesId(species.getId());
            obs.setCategoryId(species.getCategoryId());
            if (!StringUtils.hasText(obs.getReportedCommonName())) {
                obs.setReportedCommonName(species.getCommonName());
            }
        }
        obs.setStatus(PlantStatuses.APPROVED);
        obs.setIsPublic(1);
        obs.setPublishedAt(LocalDateTime.now());
        obs.setApprovedTime(LocalDateTime.now());
        plantObservationMapper.updateById(obs);
        recordReview(observationId, reviewerId, "APPROVED", req.comment());
        // 学生端「消息通知」的审核结果来源（植物模块此前不发通知，页面永远是空的）
        notificationService.sendNotification(
                obs.getSubmitterId(),
                "观察已通过审核",
                "你的观察《" + observationName(obs) + "》已通过教师审核，现在可以在展廊被其他同学看到。",
                "plant-audit",
                observationId);
    }

    /** 驳回：学生可修改后重新提交。 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long observationId, ReviewDecisionRequest req, Long reviewerId) {
        PlantObservation obs = requireSubmittable(observationId);
        if (!StringUtils.hasText(req.comment())) {
            throw new BusinessException("驳回时必须填写意见");
        }
        obs.setStatus(PlantStatuses.REJECTED);
        plantObservationMapper.updateById(obs);
        recordReview(observationId, reviewerId, "REJECTED", req.comment());
        notificationService.sendNotification(
                obs.getSubmitterId(),
                "观察被驳回",
                "你的观察《" + observationName(obs) + "》未通过审核，意见：" + req.comment()
                        + "。修改后可在「我的植物」重新提交。",
                "plant-audit",
                observationId);
    }

    /** 批量审核（V4 §77）：同一意见通过/驳回多条 SUBMITTED 记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void batchReview(List<Long> observationIds, String action, String comment, Long reviewerId) {
        if (observationIds == null || observationIds.isEmpty()) {
            throw new BusinessException("请选择待审核记录");
        }
        ReviewDecisionRequest decision = new ReviewDecisionRequest(action, comment, null);
        for (Long id : observationIds) {
            if ("APPROVED".equals(action)) {
                approve(id, decision, reviewerId);
            } else if ("REJECTED".equals(action)) {
                reject(id, decision, reviewerId);
            } else {
                throw new BusinessException("批量审核 action 必须为 APPROVED 或 REJECTED");
            }
        }
    }

    /** 通知里展示的观察名称（学生填报名优先，其次绑定物种名，最后兜底）。 */
    private String observationName(PlantObservation obs) {
        if (StringUtils.hasText(obs.getReportedCommonName())) {
            return obs.getReportedCommonName();
        }
        if (obs.getSpeciesId() != null) {
            PlantSpecies species = plantSpeciesMapper.selectById(obs.getSpeciesId());
            if (species != null && StringUtils.hasText(species.getCommonName())) {
                return species.getCommonName();
            }
        }
        return "植物观察";
    }

    /** 推荐为优秀观察 / 取消精选。 */
    @Transactional(rollbackFor = Exception.class)
    public void setFeatured(Long observationId, boolean featured, Long reviewerId) {
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        if (obs == null) {
            throw new BusinessException("观察记录不存在");
        }
        if (!PlantStatuses.APPROVED.equals(obs.getStatus())) {
            throw new BusinessException("只有审核通过的记录可以推荐为优秀观察");
        }
        obs.setFeatured(featured ? 1 : 0);
        if (featured) {
            obs.setFeaturedAt(LocalDateTime.now());
            obs.setFeaturedBy(reviewerId);
        }
        plantObservationMapper.updateById(obs);
        if (featured) {
            notificationService.sendNotification(
                    obs.getSubmitterId(),
                    "观察被评为优秀",
                    "你的观察《" + observationName(obs) + "》被教师推荐为优秀观察，已出现在首页精选。",
                    "plant-feature",
                    observationId);
        }
    }

    private PlantObservation requireSubmittable(Long observationId) {
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        if (obs == null) {
            throw new BusinessException("观察记录不存在");
        }
        if (!PlantStatuses.SUBMITTED.equals(obs.getStatus())) {
            throw new BusinessException("该记录不在待审核状态");
        }
        return obs;
    }

    private void recordReview(Long observationId, Long reviewerId, String action, String comment) {
        PlantReview review = new PlantReview();
        review.setObservationId(observationId);
        review.setReviewerId(reviewerId);
        review.setAction(action);
        review.setComment(comment);
        review.setReviewedAt(LocalDateTime.now());
        plantReviewMapper.insert(review);
    }

    private ReviewItemVO toItem(PlantObservation obs, Map<Long, String> userNames, PlantPhoto cover) {
        ReviewItemVO vo = new ReviewItemVO();
        vo.setObservationId(obs.getId());
        vo.setCoverUrl(cover != null ? cover.getFileUrl() : null);
        vo.setSpeciesId(obs.getSpeciesId());
        vo.setCommonName(loadSpeciesName(obs.getSpeciesId()));
        vo.setReportedCommonName(obs.getReportedCommonName());
        vo.setSubmitterId(obs.getSubmitterId());
        vo.setSubmitterName(userNames.get(obs.getSubmitterId()));
        vo.setClassName(obs.getClassNameSnapshot());
        vo.setProvinceName(obs.getProvinceName());
        vo.setCityName(obs.getCityName());
        vo.setDistrictName(obs.getDistrictName());
        vo.setObservedAt(obs.getObservedAt());
        vo.setSubmitTime(obs.getSubmitTime());
        vo.setStatus(obs.getStatus());
        vo.setClassId(obs.getClassId());
        vo.setPhotoCount((int) photoService.countPhotos(obs.getId()));
        return vo;
    }

    private String loadSpeciesName(Long speciesId) {
        if (speciesId == null) {
            return null;
        }
        PlantSpecies species = plantSpeciesMapper.selectById(speciesId);
        return species != null ? species.getCommonName() : null;
    }

    private PlantPhoto coverOf(Long observationId) {
        List<PlantPhoto> photos = photoService.listByObservation(observationId);
        return photos.isEmpty() ? null : photos.get(0);
    }

    private Map<Long, String> loadUserNames(List<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
            names.put(user.getId(), com.jingxuan.plant.PlantPrivacy.displayName(user.getRealName(), showRealName));
        }
        return names;
    }
}