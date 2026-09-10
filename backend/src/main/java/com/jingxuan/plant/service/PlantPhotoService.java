package com.jingxuan.plant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.plant.entity.PlantObservation;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.mapper.PlantObservationMapper;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import com.jingxuan.plant.storage.PlantPhotoStorageService;
import com.jingxuan.plant.storage.PlantStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/** 观察照片：上传（委托本地持久化存储）、封面、排序、删除（含物理文件清理）。 */
@Slf4j
@Service
public class PlantPhotoService {

    private final PlantPhotoMapper plantPhotoMapper;
    private final PlantObservationMapper plantObservationMapper;
    private final PlantPhotoStorageService storageService;
    private final PlantStorageProperties storageProperties;

    public PlantPhotoService(PlantPhotoMapper plantPhotoMapper,
                             PlantObservationMapper plantObservationMapper,
                             PlantPhotoStorageService storageService,
                             PlantStorageProperties storageProperties) {
        this.plantPhotoMapper = plantPhotoMapper;
        this.plantObservationMapper = plantObservationMapper;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
    }

    public List<PlantPhoto> listByObservation(Long observationId) {
        return plantPhotoMapper.selectList(Wrappers.<PlantPhoto>lambdaQuery()
                .eq(PlantPhoto::getObservationId, observationId)
                .orderByAsc(PlantPhoto::getSortOrder)
                .orderByAsc(PlantPhoto::getId));
    }

    public long countPhotos(Long observationId) {
        return plantPhotoMapper.selectCount(Wrappers.<PlantPhoto>lambdaQuery()
                .eq(PlantPhoto::getObservationId, observationId));
    }

    /** 上传：存储服务负责校验/落盘/缩略图；DB 失败时回滚物理文件。 */
    @Transactional(rollbackFor = Exception.class)
    public PlantPhoto upload(Long observationId, Long operatorId, MultipartFile file, String organType) {
        if (countPhotos(observationId) >= storageProperties.getMaxPhotoCount()) {
            throw new BusinessException("一条观察记录最多上传" + storageProperties.getMaxPhotoCount() + "张图片");
        }
        PlantPhotoStorageService.StoredPlantPhoto stored = storageService.save(observationId, file);
        PlantPhoto photo = new PlantPhoto();
        photo.setObservationId(observationId);
        photo.setUploaderId(operatorId);
        photo.setFileName(file.getOriginalFilename());
        photo.setFileUrl(stored.originalUrl());
        photo.setThumbnailUrl(stored.thumbnailUrl());
        photo.setFileSize(file.getSize());
        photo.setMimeType(file.getContentType());
        photo.setOrganType(normalizeOrganType(organType));
        long existing = countPhotos(observationId);
        photo.setIsCover(existing == 0 ? 1 : 0);
        photo.setSortOrder((int) existing);
        try {
            plantPhotoMapper.insert(photo);
        } catch (RuntimeException e) {
            // 数据库失败：删除刚写入的物理文件，保持一致性
            storageService.deleteFiles(observationId, stored.storedFileName());
            throw e;
        }
        return photo;
    }

    /** 记录所有者（草稿/驳回状态）删除图片（DB + 物理文件）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long observationId, Long photoId, Long operatorId) {
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        PlantPhoto photo = plantPhotoMapper.selectById(photoId);
        if (obs == null || photo == null || !photo.getObservationId().equals(observationId)) {
            throw new BusinessException("照片不存在");
        }
        if (!obs.getSubmitterId().equals(operatorId)) {
            throw new BusinessException("无权删除他人记录的照片");
        }
        String status = obs.getStatus();
        if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException("当前状态不允许删除照片");
        }
        String storedName = fileNameOf(photo.getFileUrl());
        plantPhotoMapper.deleteById(photoId);
        storageService.deleteFiles(observationId, storedName);
        if (Integer.valueOf(1).equals(photo.getIsCover())) {
            List<PlantPhoto> rest = listByObservation(observationId);
            if (!rest.isEmpty()) {
                PlantPhoto first = rest.get(0);
                first.setIsCover(1);
                plantPhotoMapper.updateById(first);
            }
        }
    }

    /** 修改照片器官标签（草稿/驳回状态、记录所有者）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrgan(Long observationId, Long photoId, String organType, Long operatorId) {
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        PlantPhoto photo = plantPhotoMapper.selectById(photoId);
        if (obs == null || photo == null || !photo.getObservationId().equals(observationId)) {
            throw new BusinessException("照片不存在");
        }
        if (!obs.getSubmitterId().equals(operatorId)) {
            throw new BusinessException("无权操作他人记录的照片");
        }
        String status = obs.getStatus();
        if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException("当前状态不允许修改照片");
        }
        photo.setOrganType(normalizeOrganType(organType));
        plantPhotoMapper.updateById(photo);
    }

    /** 删除某观察记录全部照片（管理员治理/删除草稿级联）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAllForObservation(Long observationId) {
        plantPhotoMapper.delete(Wrappers.<PlantPhoto>lambdaQuery()
                .eq(PlantPhoto::getObservationId, observationId));
        storageService.deleteObservationFiles(observationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setCover(Long observationId, Long photoId, Long operatorId) {
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        if (obs == null || !obs.getSubmitterId().equals(operatorId)) {
            throw new BusinessException("无权操作该记录");
        }
        PlantPhoto photo = plantPhotoMapper.selectById(photoId);
        if (photo == null || !photo.getObservationId().equals(observationId)) {
            throw new BusinessException("照片不存在");
        }
        clearCovers(observationId);
        photo.setIsCover(1);
        plantPhotoMapper.updateById(photo);
    }

    /** 按给定顺序调整 sort_order；第一个同时设为封面。 */
    @Transactional(rollbackFor = Exception.class)
    public void reorder(Long observationId, List<Long> photoIds, Long operatorId) {
        if (photoIds == null || photoIds.size() < 2) {
            return;
        }
        PlantObservation obs = plantObservationMapper.selectById(observationId);
        if (obs == null || !obs.getSubmitterId().equals(operatorId)) {
            throw new BusinessException("无权操作该记录");
        }
        clearCovers(observationId);
        int sort = 0;
        for (Long photoId : photoIds) {
            PlantPhoto photo = plantPhotoMapper.selectById(photoId);
            if (photo == null || !photo.getObservationId().equals(observationId)) {
                continue;
            }
            photo.setSortOrder(sort);
            photo.setIsCover(sort == 0 ? 1 : 0);
            plantPhotoMapper.updateById(photo);
            sort++;
        }
    }

    private void clearCovers(Long observationId) {
        List<PlantPhoto> photos = listByObservation(observationId);
        for (PlantPhoto p : photos) {
            if (Integer.valueOf(1).equals(p.getIsCover())) {
                p.setIsCover(0);
                plantPhotoMapper.updateById(p);
            }
        }
    }

    /** 从公开 URL 中提取存储文件名（最后一段）。 */
    private String fileNameOf(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }
        int idx = fileUrl.lastIndexOf('/');
        return idx >= 0 ? fileUrl.substring(idx + 1) : fileUrl;
    }

    private String normalizeOrganType(String organType) {
        if (organType == null || organType.isBlank()) {
            return "WHOLE";
        }
        String upper = organType.trim().toUpperCase();
        Set<String> allowed = Set.of("WHOLE", "LEAF", "FLOWER", "FRUIT", "BARK", "SEED", "OTHER");
        return allowed.contains(upper) ? upper : "OTHER";
    }
}
