package com.jingxuan.plant.storage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jingxuan.plant.entity.PlantPhoto;
import com.jingxuan.plant.mapper.PlantPhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 图片健康检查与一致性扫描（V3 §18）：只报告，不自动删除。 */
@Service
@RequiredArgsConstructor
public class PlantMediaConsistencyService {

    private final PlantPhotoStorageService storageService;
    private final PlantPhotoMapper plantPhotoMapper;

    public Map<String, Object> health() {
        Map<String, Object> report = new HashMap<>();
        Path root = storageService.rootPath();
        report.put("root", root.toString());
        report.put("exists", Files.exists(root));
        report.put("readable", Files.isReadable(root));
        report.put("writable", Files.isWritable(root));
        report.put("originalExists", Files.isDirectory(root.resolve("original")));
        report.put("thumbnailExists", Files.isDirectory(root.resolve("thumbnail")));
        return report;
    }

    public Map<String, Object> consistency() {
        Map<String, Object> report = new HashMap<>();
        List<PlantPhoto> photos = plantPhotoMapper.selectList(Wrappers.<PlantPhoto>lambdaQuery());
        List<String> dbWithoutFile = new ArrayList<>();
        List<String> illegalUrls = new ArrayList<>();
        Set<String> validPrefixes = Set.of(storageService.rootPath().resolve("original").toString(),
                storageService.rootPath().resolve("thumbnail").toString());
        for (PlantPhoto photo : photos) {
            String url = photo.getFileUrl();
            if (url == null || (!url.startsWith("/media/plants/original/") && !url.startsWith("/media/plants/thumbnail/"))) {
                illegalUrls.add(url == null ? "null" : url);
                continue;
            }
            if (!storageService.existsByUrl(url)) {
                dbWithoutFile.add(url);
            }
        }
        report.put("dbRows", photos.size());
        report.put("dbWithoutFile", dbWithoutFile.size());
        report.put("dbWithoutFileExamples", dbWithoutFile.stream().limit(10).toList());
        report.put("illegalUrls", illegalUrls.size());
        report.put("illegalUrlExamples", illegalUrls.stream().limit(10).toList());
        // 磁盘有但 DB 无
        List<Long> diskIds = storageService.listObservationIds();
        Set<Long> dbIds = photos.stream().map(PlantPhoto::getObservationId).collect(Collectors.toSet());
        List<Long> orphanDirs = diskIds.stream().filter(id -> !dbIds.contains(id)).toList();
        report.put("diskObservationDirs", diskIds.size());
        report.put("orphanObservationDirs", orphanDirs.size());
        report.put("orphanObservationDirExamples", orphanDirs.stream().limit(10).toList());
        return report;
    }
}
