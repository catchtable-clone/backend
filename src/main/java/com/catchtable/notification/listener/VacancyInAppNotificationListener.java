package com.catchtable.notification.listener;

import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.event.VacancyEvent;
import com.catchtable.notification.service.NotificationService;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.user.entity.User;
import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.entity.VacancyStatus;
import com.catchtable.vacancy.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VacancyInAppNotificationListener {

    private final VacancyRepository vacancyRepository;
    private final NotificationService notificationService;

    // 메인 트랜잭션이 커밋된 직후에 비동기적으로 실행됨
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVacancyEvent(VacancyEvent event) {
        StoreRemain storeRemain = event.getStoreRemain();
        Long remainId = storeRemain.getId();

        log.info("[알림] 빈자리 발생 이벤트 수신: remainId = {}", remainId);

        if (storeRemain.getRemainTeam() <= 0) {
            return;
        }

        List<Vacancy> subscribers = vacancyRepository.findWithUserByStoreRemain_IdAndStatusAndIsDeletedFalse(
                remainId, VacancyStatus.ACTIVE);

        if (subscribers.isEmpty()) {
            return;
        }

        String storeName = storeRemain.getStore().getStoreName();
        String remainDate = storeRemain.getRemainDate().toString();
        String remainTime = storeRemain.getRemainTime().toString();

        for (Vacancy vacancy : subscribers) {
            User user = vacancy.getUser();
            if (user == null) continue;

            String title = "빈자리 알림";
            String content = String.format("%s %s %s에 빈자리가 발생했습니다! 지금 바로 예약하세요.",
                    storeName, remainDate, remainTime);

            // 알림 생성
            notificationService.createNotification(user, NotificationType.VACANCY, title, content, storeRemain.getStore().getId());
        }
    }
}
