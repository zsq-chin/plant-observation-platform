package com.jingxuan.plant.vo;

import com.jingxuan.plant.entity.PlantObservation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生端「我的植物」列表项。
 *
 * <p>在观察记录之上补充封面缩略图与照片数量：列表页需要缩略图，
 * 而 {@link PlantObservation} 实体本身不含照片信息（照片在 plant_photo 表）。</p>
 */
@Getter
@Setter
@Schema(description = "我的观察列表项（含封面）")
public class MyObservationVO extends PlantObservation {

    @Schema(description = "封面图地址（优先 thumbnailUrl，其次 fileUrl）")
    private String coverUrl;

    @Schema(description = "照片数量")
    private Integer photoCount;
}
