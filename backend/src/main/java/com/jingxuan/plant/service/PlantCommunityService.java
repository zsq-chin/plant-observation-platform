package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.entity.SysUser;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import com.jingxuan.modules.notification.service.NotificationService;
import com.jingxuan.modules.sensitive.service.DeepSeekReviewService;
import com.jingxuan.plant.PlantStatuses;
import com.jingxuan.plant.entity.PlantComment;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantRating;
import com.jingxuan.plant.entity.PlantSpecies;
import com.jingxuan.plant.mapper.PlantCommentMapper;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantRatingMapper;
import com.jingxuan.plant.mapper.PlantSpeciesMapper;
import com.jingxuan.plant.vo.CommentVO;
import com.jingxuan.plant.vo.TeacherCommentVO;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 社区互动：评论/回复、教师点评置顶与治理、1~5 星评价。 */
@Service
@RequiredArgsConstructor
public class PlantCommunityService {

    @Value("${plant.privacy.show-real-name:true}")
    private boolean showRealName;

    private final PlantCommentMapper commentMapper;
    private final PlantRatingMapper ratingMapper;
    private final PlantObservationMapper observationMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final SysUserMapper sysUserMapper;
    private final DeepSeekReviewService deepSeekReviewService;
    private final NotificationService notificationService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    // ---------- 评论 ----------

    public List<CommentVO> listComments(Long observationId) {
        requirePublicObservation(observationId);
        List<PlantComment> comments = commentMapper.selectList(Wrappers.<PlantComment>lambdaQuery()
                .eq(PlantComment::getObservationId, observationId)
                .eq(PlantComment::getStatus, "NORMAL")
                .orderByDesc(PlantComment::getIsPinned)
                .orderByAsc(PlantComment::getId));
        return toCommentVOs(comments);
    }

    private static final int COMMENT_MAX_PER_MINUTE = 20;
    private static final java.time.Duration COMMENT_WINDOW = java.time.Duration.ofMinutes(1);

    /** 发表评论/回复；教师评论带身份标识（管理员视同教师）。 */
    @Transactional(rollbackFor = Exception.class)
    public PlantComment addComment(Long observationId, Long userId, String roleCode,
                                   String content, Long parentId) {
        PlantObservation target = requirePublicObservation(observationId);
        assertCommentRateAllowed(userId);
        if (!StringUtils.hasText(content) || content.trim().length() > 1000) {
            throw new BusinessException("评论内容不能为空且不超过1000字");
        }
        String text = content.trim();
        DeepSeekReviewService.ReviewResult reviewResult = deepSeekReviewService.review(text, "comment");
        if (reviewResult != null && !reviewResult.isPassed()) {
            throw new BusinessException("评论内容未通过内容安全校验");
        }
        PlantComment parent = null;
        if (parentId != null) {
            parent = commentMapper.selectById(parentId);
            if (parent == null || !parent.getObservationId().equals(observationId)
                    || !"NORMAL".equals(parent.getStatus())) {
                throw new BusinessException("回复的评论不存在");
            }
        }
        boolean teacher = "ROLE_TEACHER".equals(roleCode) || "ROLE_ADMIN".equals(roleCode);
        PlantComment comment = new PlantComment();
        comment.setObservationId(observationId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setRootId(parent == null ? null : (parent.getRootId() != null ? parent.getRootId() : parent.getId()));
        comment.setContent(text);
        comment.setIsTeacherComment(teacher ? 1 : 0);
        comment.setIsPinned(0);
        comment.setStatus("NORMAL");
        commentMapper.insert(comment);
        // 作者本人评论不通知自己；教师点评单独措辞
        if (target != null && target.getSubmitterId() != null && !target.getSubmitterId().equals(userId)) {
            notificationService.sendNotification(
                    target.getSubmitterId(),
                    teacher ? "收到教师点评" : "收到新评论",
                    (teacher ? "教师点评了" : "有同学评论了") + "你的观察："
                            + (text.length() > 40 ? text.substring(0, 40) + "…" : text),
                    "plant-comment",
                    observationId);
        }
        return comment;
    }

    /** 作者删除本人评论；管理员可删除任意评论。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId, boolean admin) {
        PlantComment comment = commentMapper.selectById(commentId);
        if (comment == null || "DELETED".equals(comment.getStatus())) {
            throw new BusinessException("评论不存在");
        }
        if (!admin && !comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }
        comment.setStatus("DELETED");
        commentMapper.updateById(comment);
    }

    /** 教师/管理员隐藏不当评论。 */
    @Transactional(rollbackFor = Exception.class)
    public void hideComment(Long commentId) {
        PlantComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        comment.setStatus("HIDDEN");
        commentMapper.updateById(comment);
    }

    /** 教师置顶/取消置顶自己的专业点评（管理员可置顶任意）。 */
    @Transactional(rollbackFor = Exception.class)
    public void pinComment(Long commentId, Long operatorId, boolean admin, boolean pinned) {
        PlantComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!admin && !comment.getUserId().equals(operatorId)) {
            throw new BusinessException("只能置顶自己的点评");
        }
        if (!admin && !Integer.valueOf(1).equals(comment.getIsTeacherComment())) {
            throw new BusinessException("只有教师点评可以置顶");
        }
        comment.setIsPinned(pinned ? 1 : 0);
        commentMapper.updateById(comment);
    }

    /** 首页：最新教师置顶点评。 */
    public List<TeacherCommentVO> latestPinnedTeacherComments(int limit) {
        List<PlantComment> comments = commentMapper.selectList(Wrappers.<PlantComment>lambdaQuery()
                .eq(PlantComment::getIsTeacherComment, 1)
                .eq(PlantComment::getIsPinned, 1)
                .eq(PlantComment::getStatus, "NORMAL")
                .orderByDesc(PlantComment::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> obsIds = comments.stream().map(PlantComment::getObservationId).distinct().toList();
        Map<Long, PlantObservation> obsMap = observationMapper.selectBatchIds(obsIds).stream()
                .collect(Collectors.toMap(PlantObservation::getId, Function.identity()));
        List<Long> speciesIds = obsMap.values().stream().map(PlantObservation::getSpeciesId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, PlantSpecies> speciesMap = speciesIds.isEmpty() ? Map.of()
                : speciesMapper.selectBatchIds(speciesIds).stream()
                .collect(Collectors.toMap(PlantSpecies::getId, Function.identity()));
        Map<Long, String> userNames = new HashMap<>();
        List<Long> userIds = comments.stream().map(PlantComment::getUserId).distinct().toList();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNames.put(user.getId(), com.jingxuan.plant.PlantPrivacy.displayName(user.getRealName(), showRealName));
            }
        }
        List<TeacherCommentVO> result = new ArrayList<>();
        for (PlantComment comment : comments) {
            PlantObservation obs = obsMap.get(comment.getObservationId());
            if (obs == null) {
                continue;
            }
            TeacherCommentVO vo = new TeacherCommentVO();
            vo.setCommentId(comment.getId());
            vo.setObservationId(obs.getId());
            PlantSpecies species = obs.getSpeciesId() != null ? speciesMap.get(obs.getSpeciesId()) : null;
            vo.setPlantName(species != null ? species.getCommonName() : obs.getReportedCommonName());
            vo.setTeacherName(userNames.get(comment.getUserId()));
            vo.setContent(comment.getContent());
            vo.setCreateTime(comment.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    /** 评论防刷（V4 §52）：单用户 1 分钟内最多 N 条评论/回复。 */
    private void assertCommentRateAllowed(Long userId) {
        String key = "jingxuan:plant:comment:" + userId + ":" + (System.currentTimeMillis() / 60_000L);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, COMMENT_WINDOW);
        }
        if (count != null && count > COMMENT_MAX_PER_MINUTE) {
            throw new BusinessException("评论太频繁，请稍后再试");
        }
    }

    // ---------- 星级评价 ----------

    /** 新增或更新本人评分（一人一记录一条）。 */
    @Transactional(rollbackFor = Exception.class)
    public PlantRating upsertRating(Long observationId, Long userId, int score) {
        if (score < 1 || score > 5) {
            throw new BusinessException("评分必须是1~5星");
        }
        requirePublicObservation(observationId);
        PlantRating existing = ratingMapper.selectOne(Wrappers.<PlantRating>lambdaQuery()
                .eq(PlantRating::getObservationId, observationId)
                .eq(PlantRating::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setScore(score);
            ratingMapper.updateById(existing);
            return existing;
        }
        PlantRating rating = new PlantRating();
        rating.setObservationId(observationId);
        rating.setUserId(userId);
        rating.setScore(score);
        ratingMapper.insert(rating);
        return rating;
    }

    // ---------- 内部 ----------

    /** 校验观察已公开可互动，并返回该记录（评论时用于通知作者）。 */
    private PlantObservation requirePublicObservation(Long observationId) {
        PlantObservation obs = observationMapper.selectById(observationId);
        if (obs == null || !PlantStatuses.APPROVED.equals(obs.getStatus())
                || !Integer.valueOf(1).equals(obs.getIsPublic())) {
            throw new BusinessException("该观察记录不存在或未公开，无法互动");
        }
        return obs;
    }

    private List<CommentVO> toCommentVOs(List<PlantComment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNames = new HashMap<>();
        List<Long> userIds = comments.stream().map(PlantComment::getUserId).distinct().toList();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNames.put(user.getId(), com.jingxuan.plant.PlantPrivacy.displayName(user.getRealName(), showRealName));
            }
        }
        return comments.stream().map(c -> {
            CommentVO vo = new CommentVO();
            vo.setCommentId(c.getId());
            vo.setObservationId(c.getObservationId());
            vo.setUserId(c.getUserId());
            vo.setUserName(userNames.get(c.getUserId()));
            vo.setIsTeacherComment(Integer.valueOf(1).equals(c.getIsTeacherComment()));
            vo.setIsPinned(Integer.valueOf(1).equals(c.getIsPinned()));
            vo.setParentId(c.getParentId());
            vo.setRootId(c.getRootId());
            vo.setContent(c.getContent());
            vo.setCreateTime(c.getCreateTime());
            return vo;
        }).toList();
    }
}