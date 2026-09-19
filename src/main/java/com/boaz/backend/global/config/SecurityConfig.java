package com.boaz.backend.global.config;

import com.boaz.backend.global.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.boaz.backend.global.oauth.CustomOAuth2UserService;
import com.boaz.backend.global.oauth.OAuth2AuthenticationFailureHandler;
import com.boaz.backend.global.oauth.OAuth2AuthenticationSuccessHandler;
import com.boaz.backend.global.security.AdminUserDetailsService;
import com.boaz.backend.global.security.CustomAccessDeniedHandler;
import com.boaz.backend.global.security.CustomAuthenticationEntryPoint;
import com.boaz.backend.global.security.JwtAuthenticationFilter;
import com.boaz.backend.global.security.JwtProvider;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
// 이게 꺼져 있으면 @PreAuthorize가 무시된다.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final AdminUserDetailsService adminUserDetailsService;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 0) 에러/비동기 디스패치는 인가 대상이 아니다. 빼면 예외 발생 시 /error 내부 forward가
                        //    denyAll에 걸려 403으로 덮이고 진짜 에러가 안 보인다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC, DispatcherType.FORWARD)
                        .permitAll()

                        // 1) 공개 경로 — 화이트리스트. 여기 없는 경로는 4)에서 전부 막힌다.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/archiving/**",
                                "/api/v1/curriculums",
                                "/api/v1/reviews",
                                "/api/v1/recruitment/status",
                                "/api/v1/recruitment/deadline",
                                "/api/v1/recruitment/questions",
                                "/api/v1/recruitment/*").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/recruitment/subscriptions",
                                "/api/v1/auth/admin/login",
                                "/api/v1/auth/admin/refresh",
                                "/api/v1/auth/user/refresh").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // 막히면 CodeDeploy ValidateService 훅과 ALB 헬스체크가 실패해 배포가 죽는다.
                        .requestMatchers("/actuator/health").permitAll()

                        // 2) USER 경로 — 기존 그대로
                        .requestMatchers("/api/v1/auth/user/logout").hasRole("USER")
                        // 지원서 제출/임시저장/조회 (User 인증 필요)
                        .requestMatchers(HttpMethod.POST, "/api/v1/recruitment/*/applications").hasRole("USER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/recruitment/*/applications/draft").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/recruitment/*/applications/me").hasRole("USER")
                        // 내 정보 조회 (User 인증 필요)
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").hasRole("USER")

                        // 3) admin 경로 — 기능별 판정은 컨트롤러에서(@PreAuthorize) 한다.
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("MASTER","SUPER","TEAM","HOST")
                        .requestMatchers("/api/v1/auth/admin/logout").hasAnyRole("MASTER","SUPER","TEAM","HOST")

                        // 4) 나머지는 전부 막는다
                        .anyRequest().denyAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .baseUri("/oauth2/authorize")
                                .authorizationRequestRepository(authorizationRequestRepository)
                        )
                        .redirectionEndpoint(endpoint -> endpoint
                                .baseUri("/login/oauth2/code/*")
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, adminUserDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
