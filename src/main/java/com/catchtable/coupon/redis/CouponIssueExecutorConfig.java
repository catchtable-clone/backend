package com.catchtable.coupon.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 쿠폰 발급 Lua 호출 전용 스레드 풀.
 *
 * 기본 ForkJoinPool(코어 수≈8) 을 쓰면 수백 동시 호출이 큐에서 대기하다가
 * @TimeLimiter 에 걸려 인프라 장애가 아닌데도 폴백된다.
 * 발급 호출은 1ms 안에 끝나므로 풀 크기 100 으로도 수천 TPS 처리 가능.
 *
 * 명시 풀로 분리하는 추가 효과:
 *  - 톰캣 워커와 격리 → 발급 호출이 톰캣 워커를 점유하지 않음.
 *  - 다른 도메인 비동기 작업과 자원 경쟁 없음.
 *  - ThreadPoolTaskExecutor 의 graceful shutdown 으로 SIGTERM 시 진행 중 작업 보장.
 */
@Configuration
public class CouponIssueExecutorConfig {

    @Bean(name = "couponIssueExecutor")
    public Executor couponIssueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(1_000);
        executor.setThreadNamePrefix("coupon-issue-");
        // SIGTERM 수신 시 진행 중인 발급 호출이 끝날 때까지 최대 10초 대기.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
