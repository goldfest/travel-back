package com.travelapp.route.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Route Planning Service API")
                        .description("API для управления маршрутами путешествий")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Иван Лобашов")
                                .email("vano00189@mail.ru"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://springdoc.org")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8082" + contextPath)
                                .description("Локальный сервер"),
                        new Server()
                                .url("https://api.travelapp.com" + contextPath)
                                .description("Продакшен сервер")
                ));
    }
}