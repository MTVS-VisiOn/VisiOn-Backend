package mtvs.onvision.vision.common.config;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler  jwtAccessDeniedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http.cors(cors -> cors.disable())
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .authorizeHttpRequests(auth ->auth
                        .requestMatchers("/api/users/signup", "/api/auth/login").anonymous()
                        .requestMatchers("/api/users/refresh").permitAll()
                        .requestMatchers("/api/users/guardian/register-code").hasAuthority("WARD")
                        .requestMatchers("/api/users/device/register-code").hasAuthority("WARD")
                        .requestMatchers("/api/users/device-fid").hasAnyAuthority("WARD","GUARDIAN")
                        .requestMatchers("/api/users/me").hasAnyAuthority("WARD","GUARDIAN")
                        .requestMatchers("/api/common", "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/presence").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.GET,"/api/presence").hasAuthority("GUARDIAN")
                        .requestMatchers(HttpMethod.POST,"/api/locations").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.GET,"/api/locations").hasAuthority("GUARDIAN")
                        .requestMatchers("/api/locations/search").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.POST,"/api/favorites").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.GET,"/api/favorites/search").hasAnyAuthority("WARD","GUARDIAN")
                        .requestMatchers(HttpMethod.PATCH,"/api/favorites/**").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.DELETE,"/api/favorites/**").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.POST,"/api/navigations/**").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.GET,"/api/navigations/map").hasAuthority("GUARDIAN")
                        .requestMatchers(HttpMethod.GET,"/api/navigations/**").hasAnyAuthority("WARD","GUARDIAN")
                        .requestMatchers(HttpMethod.PATCH,"/api/navigations/**").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.GET, "/api/ice-servers").hasAnyAuthority("WARD","GUARDIAN")
                        .requestMatchers("/api/alerts/detect/obstacle").hasAuthority("WARD")
                        .requestMatchers(HttpMethod.GET,"/api/alerts/**").hasAuthority("GUARDIAN")
                        .requestMatchers("/api/commands/instruction").hasAuthority("GUARDIAN")
                        .requestMatchers(HttpMethod.GET,"/api/commands").hasAuthority("GUARDIAN")
                        .requestMatchers("/api/instructions/**").hasAuthority("GUARDIAN")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exp -> exp
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
