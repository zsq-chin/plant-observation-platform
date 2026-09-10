package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.entity.SysNotification;
import com.jingxuan.mapper.SysNotificationMapper;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantReview;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantReviewMapper;
import com.jingxuan.plant.vo.StudentDashboardVO;
import com.jingxuan.plant.vo.TeacherDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工作台统计（V4 §6/§81）。 */
@Service
@RequiredArgsConstructor
public class PlantDashboardService {

    private final PlantObservationMapper observationMapper;
    private final PlantReviewMapper reviewMapper;
    private final SysNotificationMapper notificationMapper;

    public StudentDashboardVO student(Long userId) {
        StudentDashboardVO vo = new StudentDashboardVO();
        List<PlantObservation> mine = observationMapper.selectList(Wrappers.<PlantObservation>lambdaQuery()
                .select(PlantObservation::getStatus)
                .eq(PlantObservation::getSubmitterId, userId));
        var counts = mine.stream().collect(Collectors.groupingBy(PlantObservation::getStatus, Collectors.counting()));
        vo.setDraftCount(counts.getOrDefault(PlantStatuses.DRAFT, 0L));
        vo.setSubmittedCount(counts.getOrDefault(PlantStatuses.SUBMITTED, 0L));
        vo.setApprovedCount(counts.getOrDefault(PlantStatuses.APPROVED, 0L));
        vo.setRejectedCount(counts.getOrDefault(PlantStatuses.REJECTED, 0L));
        vo.setUnreadNotificationCount(notificationMapper.selectCount(Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0)));
        return vo;
    }

    public TeacherDashboardVO teacher() {
        TeacherDashboardVO vo = new TeacherDashboardVO();
        vo.setPendingCount(observationMapper.selectCount(Wrappers.<PlantObservation>lambdaQuery()
                .eq(PlantObservation::getStatus, PlantStatuses.SUBMITTED)));
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        vo.setTodaySubmittedCount(observationMapper.selectCount(Wrappers.<PlantObservation>lambdaQuery()
                .eq(PlantObservation::getStatus, PlantStatuses.SUBMITTED)
                .ge(PlantObservation::getSubmitTime, todayStart)));
        List<PlantReview> todayReviews = reviewMapper.selectList(Wrappers.<PlantReview>lambdaQuery()
                .ge(PlantReview::getCreateTime, todayStart));
        long approved = 0;
        long rejected = 0;
        for (PlantReview review : todayReviews) {
            if ("APPROVED".equals(review.getAction())) approved++;
            if ("REJECTED".equals(review.getAction())) rejected++;
        }
        vo.setTodayApprovedCount(approved);
        vo.setTodayRejectedCount(rejected);
        return vo;
    }
}
