package com.travelapp.poi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableFeignClients
public class PoiContentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PoiContentApplication.class, args);
    }
}