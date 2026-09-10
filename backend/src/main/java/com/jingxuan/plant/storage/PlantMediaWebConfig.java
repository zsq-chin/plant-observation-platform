package com.jingxuan.plant.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/** 开发/直连模式：/media/plants/** -> {plant.storage.root}/ 静态映射。 */
@Configuration
@RequiredArgsConstructor
public class PlantMediaWebConfig implements WebMvcConfigurer {

    private final PlantStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = properties.rootPath().toUri().toString();
        registry.addResourceHandler(properties.getPublicPrefix() + "/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
