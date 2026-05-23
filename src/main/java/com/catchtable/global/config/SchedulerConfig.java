package com.catchtable.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    /**
     * 스케줄러 및 시간 비교 로직에서 사용할 Clock.
     * KST(Asia/Seoul) 기준으로 고정하여 Docker 컨테이너 기본 타임존(UTC) 영향을 받지 않도록 한다.
     * LocalDateTime.now(clock) 형태로 사용하면 타임존 일관성과 테스트 가능성이 모두 확보된다.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
