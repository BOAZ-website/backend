package com.boaz.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("prod")
@RequiredArgsConstructor
public class SwaggerSecurityConfig {

    @Value("${swagger.user}")
    private String swaggerUser;

    @Value("${swagger.password}")
    private String swaggerPassword;

    @Bean
    @Order(1) // 기존 메인 체인(@Order 없음 = 맨 뒤)보다 먼저 평가되어 swagger 경로를 선점한다.
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**"
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 이 체인에만 적용되는 계정. 전역 빈으로 두지 않아 admin 인증과 섞이지 않는다.
                .userDetailsService(swaggerUserDetailsService());

        return http.build();
    }

    private UserDetailsService swaggerUserDetailsService() {
        UserDetails user = User.withUsername(swaggerUser)
                .password("{noop}" + swaggerPassword) // 팀 공용 평문 비밀번호를 그대로 비교
                .roles("SWAGGER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
