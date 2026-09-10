package com.jingxuan.plant.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 植物图片本地存储配置（V3 文档 §5）。 */
@Component
@ConfigurationProperties(prefix = "plant.storage")
public class PlantStorageProperties {

    /** 根目录，如 ./data/plant-media 或 /app/data/plant-media */
    private String root = "./data/plant-media";
    private String originalDir = "original";
    private String thumbnailDir = "thumbnail";
    /** 对外公开访问前缀，映射到根目录 */
    private String publicPrefix = "/media/plants";
    private int maxPhotoCount = 10;

    public Path rootPath() {
        return Paths.get(root).toAbsolutePath().normalize();
    }

    public Path originalPath() {
        return rootPath().resolve(originalDir);
    }

    public Path thumbnailPath() {
        return rootPath().resolve(thumbnailDir);
    }

    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
    public String getOriginalDir() { return originalDir; }
    public void setOriginalDir(String originalDir) { this.originalDir = originalDir; }
    public String getThumbnailDir() { return thumbnailDir; }
    public void setThumbnailDir(String thumbnailDir) { this.thumbnailDir = thumbnailDir; }
    public String getPublicPrefix() { return publicPrefix; }
    public void setPublicPrefix(String publicPrefix) { this.publicPrefix = publicPrefix; }
    public int getMaxPhotoCount() { return maxPhotoCount; }
    public void setMaxPhotoCount(int maxPhotoCount) { this.maxPhotoCount = maxPhotoCount; }
}
