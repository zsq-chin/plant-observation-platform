package com.jingxuan.plant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 评论条目（公开列表）。 */
@Data
public class CommentVO {

    private Long commentId;
    private Long observationId;
    private Long userId;
    private String userName;
    private Boolean isTeacherComment;
    private Boolean isPinned;
    private Long parentId;
    private Long rootId;
    private String content;
    private LocalDateTime createTime;
}
