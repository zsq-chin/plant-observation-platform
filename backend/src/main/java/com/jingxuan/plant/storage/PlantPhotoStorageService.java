package com.jingxuan.plant.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 植物图片本地存储抽象（V3 文档 §6）。
 * 只负责物理文件生命周期；数据库行由上层 PlantPhotoService 管理。
 */
public interface PlantPhotoStorageService {

    /** 保存原图并生成缩略图，返回访问信息。 */
    StoredPlantPhoto save(Long observationId, MultipartFile file);

    /** 按原始文件名（uuid.ext）删除原图与对应缩略图；文件不存在视为成功。 */
    void deleteFiles(Long observationId, String storedFileName);

    /** 删除某观察记录的全部原图/缩略图目录。 */
    void deleteObservationFiles(Long observationId);

    /** 由公开 URL 判断物理文件是否存在。 */
    boolean existsByUrl(String publicUrl);

    /** 统计某观察记录原图文件数（目录内）。 */
    long countOriginals(Long observationId);

    /** 列出全部观察目录 id（用于一致性扫描）。 */
    List<Long> listObservationIds();

    /** 返回本地根目录（健康检查/管理用）。 */
    java.nio.file.Path rootPath();

    record StoredPlantPhoto(String originalUrl, String thumbnailUrl, String storedFileName) {}
}
