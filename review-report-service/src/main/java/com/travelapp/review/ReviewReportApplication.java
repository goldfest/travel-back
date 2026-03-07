package com.travelapp.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
@EnableCaching
public class ReviewReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewReportApplication.class, args);
    }
}