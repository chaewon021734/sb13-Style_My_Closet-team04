package com.project.sb13ootbsteam04.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WeatherClientConfig {

    @Bean
    public RestClient kakaoRestClient() {
        return RestClient.builder().baseUrl("https://dapi.kakao.com")
                .requestFactory(requestFactory()).build();
    }

    @Bean
    public RestClient kmaRestClient() {
        return RestClient.builder().baseUrl("https://apis.data.go.kr")
                .requestFactory(requestFactory()).build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
