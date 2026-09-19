package com.boaz.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

/**
 * 2층({@code @PreAuthorize})을 실제로 켠 채 컨트롤러를 태우는 테스트 설정. 운영
 * {@code SecurityConfig}와 같은 모양 — {@code @EnableMethodSecurity} + URL 게이트는 인증 경계만,
 * 나머지는 {@code denyAll} — 이라 43행 × 8주체 격자 테스트가 여기에 물린다.
 *
 * <p><b>{@link TestSecurityConfig}는 건드리지 않는다.</b> 기존 컨트롤러 테스트 16개가 그 설정에 물려 있고,
 * 거기는 메서드 시큐리티가 꺼져 있어 {@code @PreAuthorize}가 무시된다 — 그 상태를 유지하는 것이 전제다.
 *
 * <p><b>이번 이슈에는 소비처가 없다.</b> {@code @PreAuthorize} 부착이 도메인 이슈(3·3.5단계) 몫이라,
 * 이 설정을 {@code @Import} 하는 격자 테스트도 거기서 붙는다.
 *
 * <p>붙일 때 확인할 것: {@code @EnableMethodSecurity}가 꺼져 있으면 인가가 조용히 통과하므로,
 * <b>403을 기대하는 테스트가 실제로 빨간불이 되는지</b>를 한 번 눈으로 봐야 한다.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class PermissionTestSecurityConfig {

    @Bean
    public SecurityFilterChain permissionTestSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC, DispatcherType.FORWARD)
                .permitAll()
                // 기능별 판정은 2층이 한다. 여기서는 인증 경계까지만 본다.
                .requestMatchers("/api/v1/admin/**").authenticated()
                .requestMatchers("/api/v1/auth/admin/logout").authenticated()
                .requestMatchers("/api/v1/auth/user/logout").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/api/v1/recruitment/*/applications").hasRole("USER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/recruitment/*/applications/draft").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/v1/recruitment/*/applications/me").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasRole("USER")
                .anyRequest().denyAll()
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
