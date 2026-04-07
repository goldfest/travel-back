package com.travelapp.graphimport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class GraphImporterApplication {
    public static void main(String[] args) {
        SpringApplication.run(GraphImporterApplication.class, args);
    }
}
