package com.jingxuan.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jingxuan.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 观察记录评论/回复。 */
@Getter
@Setter
@TableName("plant_comment")
public class PlantComment extends BaseEntity {

    private Long observationId;
    private Long userId;
    private Long parentId;
    private Long rootId;
    private String content;
    private Integer isTeacherComment;
    private Integer isPinned;
    /** NORMAL / HIDDEN / DELETED */
    private String status;
}
