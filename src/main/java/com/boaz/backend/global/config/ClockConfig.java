package com.boaz.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // JVM 기본 타임존(Asia/Seoul)을 따르는 시스템 클럭.
    // 모집 기간 판정 시각을 한 곳에서 주입받아 테스트에서 고정 가능하도록 빈으로 제공한다.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
