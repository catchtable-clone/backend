package com.catchtable.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PortoneRestClientConfig {

    @Bean
    public RestClient portoneRestClient(@Value("${portone.secret-key}") String secretKey) {
        return RestClient.builder()
                .baseUrl("https://api.portone.io")
                .defaultHeader("Authorization", "PortOne " + secretKey)
                .build();
    }
}
