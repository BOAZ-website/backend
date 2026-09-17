package com.boaz.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * prod 이외 프로파일의 Swagger 경로를 연다.
 *
 * <p>{@link SwaggerSecurityConfig}가 {@code @Profile("prod")}라 prod에서만 별도 체인이 이 경로를 선점해
 * basic auth를 건다. local·dev는 메인 체인으로 흘러오는데, 그 체인이 {@code anyRequest().denyAll()}로
 * 바뀌면서 Swagger가 403으로 죽는다.
 *
 * <p>prod는 건드리지 않는다. 두 체인의 프로파일이 배타적이라 동시에 등록되지 않는다.
 */
@Configuration
@Profile("!prod")
public class SwaggerPermitAllConfig {

    @Bean
    @Order(1) // 메인 체인(@Order 없음 = 맨 뒤)보다 먼저 평가되어 swagger 경로를 선점한다.
    public SecurityFilterChain swaggerPermitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs",
                        "/api-docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
