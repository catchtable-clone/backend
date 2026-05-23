package com.catchtable.global.lock;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final RedissonClient redissonClient;

    /**
     * 분산 락을 잡은 뒤 task를 실행하고, 끝나면 락을 해제한다.
     *
     * @param lockKey      락 키. 같은 키끼리만 직렬화된다.
     * @param waitSeconds  락을 잡으려고 기다리는 최대 시간(초).
     * @param leaseSeconds 락을 잡은 뒤 자동으로 풀리는 시간(초). 작업이 죽어도 이 시간 후 해제된다.
     * @param task         락 안에서 실행할 작업.
     */
    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("분산 락 획득 실패 (대기 {}초 초과): {}", waitSeconds, lockKey);
                throw new CustomException(ErrorCode.LOCK_TIMEOUT);
            }
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.LOCK_TIMEOUT);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
