package com.travelapp.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.storage.upload-dir:/app/uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ВАЖНО: context-path=/api уже добавится автоматически,
        // поэтому тут путь "/uploads/**" => будет доступно как "/api/uploads/**"
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + ensureTrailingSlash(uploadDir))
                .setCachePeriod(3600);
    }

    private String ensureTrailingSlash(String path) {
        if (path.endsWith("/")) return path;
        return path + "/";
    }
}