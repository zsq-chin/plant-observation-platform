package com.jingxuan.modules.work.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.jingxuan.common.PageResult;
import com.jingxuan.entity.Work;
import com.jingxuan.modules.work.dto.*;

import java.util.List;

/**
 * 旧作品域服务（Legacy Work）。
 *
 * <p>业务收口说明（《下一步开发计划：地图完善与旧功能收口》§5/§8）：
 * 学生内容提交与公开展示已统一到 {@code PlantObservation}（植物观察），
 * 前端学生端与公开端已不再调用旧作品页面与路由（MyWorks/WorkSubmit/WorkList/WorkDetail 已删除）。
 *
 * <p>本服务当前仅保留给仍在运行的旧域功能使用：教师评分、管理员内容审核、排行榜、待办、删除申请。
 * 完整下线步骤与依赖清单见 {@code docs/legacy-work-retirement.md}。
 */
public interface WorkService extends IService<Work> {

    /**
     * 创建作品（草稿状态）
     */
    Long createWork(WorkRequest request);

    /**
     * 编辑作品
     */
    void updateWork(Long id, WorkRequest request);

    /**
     * 提交审核（草稿→已提交）
     */
    void submitWork(Long id);

    /**
     * 删除作品（仅草稿或已驳回状态可删）
     */
    void deleteWork(Long id);

    /**
     * 管理员删除作品（不限制状态，清理所有关联数据）
     */
    void adminDeleteWork(Long workId);

    /**
     * 分页查询作品列表
     */
    PageResult<WorkListVO> queryWorkList(WorkQueryRequest request);

    /**
     * 获取作品详情
     */
    WorkDetailVO getWorkDetail(Long id);

    /**
     * 获取已审核通过的作品详情
     */
    WorkDetailVO getApprovedWorkDetail(Long id);

    /**
     * 获取可在公开展廊展示的作品详情。
     */
    WorkDetailVO getPublishedWorkDetail(Long id);

    /**
     * 获取当前学生自己的作品详情
     */
    WorkDetailVO getCurrentStudentWorkDetail(Long id);

    /**
     * 获取我的作品列表
     */
    List<WorkListVO> getMyWorks(Long userId);

    /**
     * 获取当前用户参与的作品
     */
    List<Work> listParticipatedWorks(Long userId);
}

