package com.travelapp.route.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "routing.yandex")
public class YandexRoutingProperties {

    /**
     * Включить реальные запросы в Yandex Routing API.
     * Если false или api-key пустой — будет fallback.
     */
    private boolean enabled = true;

    /**
     * API key Yandex Routing API
     */
    private String apiKey;

    /**
     * Базовый URL роутинга
     */
    private String baseUrl = "https://api.routing.yandex.net";

    /**
     * Язык ответа
     */
    private String lang = "ru_RU";

    /**
     * Для driving можно:
     * disabled / realtime / forecast
     */
    private String traffic = "disabled";

    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 20;
}