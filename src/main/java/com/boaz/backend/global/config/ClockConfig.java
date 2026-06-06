package com.boaz.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // Asia/Seoul 타임존을 명시적으로 고정한 시스템 클럭.
    // JVM 기본 타임존(전역 TimeZone.setDefault)에 의존하지 않고 모집 기간 판정 시각을
    // 한 곳에서 주입받아, 테스트에서 고정 가능하도록 빈으로 제공한다.
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
