package com.travelapp.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ReviewReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewReportApplication.class, args);
    }
}