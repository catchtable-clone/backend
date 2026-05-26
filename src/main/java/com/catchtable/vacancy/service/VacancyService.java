package com.catchtable.vacancy.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.vacancy.dto.write.VacancyListResponse;
import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyService {

    private final VacancyRepository vacancyRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public Long register(Long userId, Long remainId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StoreRemain storeRemain = storeRemainRepository.findById(remainId)
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        if (storeRemain.getRemainTeam() > 0) {
            throw new CustomException(ErrorCode.VACANCY_REMAIN_NOT_EXHAUSTED);
        }

        if (vacancyRepository.existsByUser_IdAndStoreRemain_IdAndIsDeletedFalse(userId, remainId)) {
            throw new CustomException(ErrorCode.VACANCY_ALREADY_REGISTERED);
        }
        
        Vacancy vacancy = new Vacancy(user, storeRemain);
        Vacancy savedVacancy = vacancyRepository.save(vacancy);

        // Redis에 구독자 정보 저장 (wait:store:{storeId}:{date}:{time})
        String redisKey = generateRedisKey(storeRemain);
        try {
            redisTemplate.opsForSet().add(redisKey, String.valueOf(userId));
            
            // TTL 설정: 예약 시간 + 1시간
            LocalDateTime expireTime = LocalDateTime.of(storeRemain.getRemainDate(), storeRemain.getRemainTime()).plusHours(1);
            Date expireDate = Date.from(expireTime.atZone(ZoneId.systemDefault()).toInstant());
            redisTemplate.expireAt(redisKey, expireDate);
            
            log.info("[빈자리 알림 등록] Redis SADD 완료: key={}, userId={}", redisKey, userId);
        } catch (Exception e) {
            log.error("[빈자리 알림 등록] Redis 저장 실패: key={}, userId={}", redisKey, userId, e);
            // Redis 저장이 실패하더라도 DB 저장은 유지하도록 예외를 던지지 않습니다.
            // 필요에 따라 예외를 던져 트랜잭션을 롤백할 수도 있습니다.
        }

        return savedVacancy.getId();
    }

    @Transactional(readOnly = true)
    public List<VacancyListResponse> getMyList(Long userId) {
        // N+1 문제 해결: EntityGraph를 통해 StoreRemain과 Store를 함께 조회 (1번의 쿼리로 처리됨)
        return vacancyRepository.findByUser_IdAndIsDeletedFalse(userId)
                .stream()
                .map(vacancy -> {
                    StoreRemain storeRemain = vacancy.getStoreRemain();
                    Store store = storeRemain.getStore();
                    return new VacancyListResponse(vacancy, storeRemain, store);
                })
                .toList();
    }

    @Transactional
    public Long delete(Long vacancyId, Long userId) {
        Vacancy vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACANCY_NOT_FOUND));
        if (vacancy.getIsDeleted()) {
            throw new CustomException(ErrorCode.VACANCY_ALREADY_DELETED);
        }
        if (!vacancy.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        vacancy.delete();

        // Redis에서 구독자 정보 제거
        StoreRemain storeRemain = vacancy.getStoreRemain();
        String redisKey = generateRedisKey(storeRemain);
        try {
            redisTemplate.opsForSet().remove(redisKey, String.valueOf(userId));
            log.info("[빈자리 알림 취소] Redis SREM 완료: key={}, userId={}", redisKey, userId);
        } catch (Exception e) {
            log.error("[빈자리 알림 취소] Redis 제거 실패: key={}, userId={}", redisKey, userId, e);
        }

        return vacancy.getId();
    }

    public String generateRedisKey(StoreRemain storeRemain) {
        return String.format("wait:store:%d:%s:%s",
                storeRemain.getStore().getId(),
                storeRemain.getRemainDate(),
                storeRemain.getRemainTime());
    }
}
