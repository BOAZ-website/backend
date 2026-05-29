package com.boaz.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/**").hasAnyRole("SUPER", "TEAM")
                .requestMatchers("/api/v1/auth/admin/logout").hasAnyRole("SUPER", "TEAM")
                .requestMatchers("/api/v1/auth/user/logout").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/api/v1/recruitment/*/applications").hasRole("USER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/recruitment/*/applications/draft").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/v1/recruitment/*/applications/me").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasRole("USER")
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"status\":401,\"error_code\":\"TOKEN_NOT_FOUND\",\"message\":\"토큰이 존재하지 않습니다.\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"status\":403,\"error_code\":\"ACCESS_DENIED\",\"message\":\"해당 리소스에 접근할 권한이 없습니다.\"}");
                })
            );
        return http.build();
    }
}
