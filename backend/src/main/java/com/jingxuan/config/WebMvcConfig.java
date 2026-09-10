package com.jingxuan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置 — CORS、静态资源映射。
 *
 * <p>植物照片访问约定：URL 为 /media/plants/{original|thumbnail}/{observationId}/{uuid}.jpg，
 * 物理文件位于 plant.storage.root（默认 ./data/plant-media），映射在本类统一声明，
 * 与容器/裸机 Nginx 的 /media/plants/ 规则保持一致。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${jingxuan.upload.path:./uploads}")
    private String uploadPath;

    @Value("${plant.storage.root:./data/plant-media}")
    private String plantMediaRoot;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absoluteUploadPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absoluteUploadPath.toUri().toString());
        // 植物照片：/media/plants/** -> {plant.storage.root}/（含 original 与 thumbnail 两个子目录）
        Path absolutePlantMedia = Paths.get(plantMediaRoot).toAbsolutePath().normalize();
        registry.addResourceHandler("/media/plants/**")
                .addResourceLocations(absolutePlantMedia.toUri().toString());
        // Springdoc / Swagger UI 静态资源
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
    }
}
