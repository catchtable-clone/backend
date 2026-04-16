package com.catchtable.notification.service;

import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.entity.VacancyStatus;
import com.catchtable.vacancy.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyNotificationService {

    private final VacancyRepository vacancyRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final Map<Long, LocalDateTime> lastNotifiedMap = new ConcurrentHashMap<>();

    @Transactional
    public void notifySubscribers(Long remainId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastNotified = lastNotifiedMap.get(remainId);

        if (lastNotified != null && lastNotified.plusMinutes(5).isAfter(now)) {
            return;
        }

        StoreRemain storeRemain = storeRemainRepository.findById(remainId)
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        if (storeRemain.getRemainTeam() <= 0) {
            return;
        }

        List<Vacancy> subscribers = vacancyRepository.findByRemainIdAndStatusAndIsDeletedFalse(
                remainId, VacancyStatus.ACTIVE);

        if (subscribers.isEmpty()) {
            return;
        }

        String storeName = storeRemain.getStore().getStoreName();
        String remainDate = storeRemain.getRemainDate().toString();
        String remainTime = storeRemain.getRemainTime().toString();

        for (Vacancy vacancy : subscribers) {
            User user = userRepository.findById(vacancy.getUserId())
                    .orElse(null);
            if (user == null) continue;

            emailService.send(user.getEmail(),
                    "[캐치테이블] 빈자리 알림",
                    String.format("%s님, %s %s %s에 빈자리가 발생했습니다! 지금 바로 예약하세요.",
                            user.getNickname(), storeName, remainDate, remainTime));

            vacancy.markNotified();
        }
        lastNotifiedMap.put(remainId, now);

        log.info("[빈자리 알림] remainId: {}, {}명에게 알림 발송", remainId, subscribers.size());
    }
}
