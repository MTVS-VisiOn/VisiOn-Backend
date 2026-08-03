package mtvs.onvision.vision.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// 수동 테스트 페이지는 prod 에서 차단한다.
// SecurityConfig 가 .anyRequest().permitAll() 로 끝나므로 규칙이 없으면 전부 공개된다.
@Configuration
@Profile("prod")
public class TestPageSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain testPageChain(HttpSecurity http){
        return http.securityMatcher("/signal-test.html")
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .build();
    }
}
