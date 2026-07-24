package mtvs.onvision.vision.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Value("${tmap.base-url}")
    private String baseUrl;
    @Value("${tmap.app-key}")
    private String appKey;

    @Bean
    public RestClient tmapRestClient() {
        System.out.println(appKey);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("appKey", appKey)
                .build();
    }
}
