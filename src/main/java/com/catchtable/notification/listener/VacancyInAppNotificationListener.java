package com.catchtable.notification.listener;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.event.VacancyEvent;
import com.catchtable.notification.service.NotificationService;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.user.entity.User;
import com.catchtable.vacancy.entity.Vacancy;
import com.catchtable.vacancy.entity.VacancyStatus;
import com.catchtable.vacancy.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VacancyInAppNotificationListener {

    private final VacancyRepository vacancyRepository;
    private final NotificationService notificationService;
    private final StoreRemainRepository storeRemainRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleVacancyEvent(VacancyEvent event) {
        Long remainId = event.getRemainId();

        StoreRemain storeRemain = storeRemainRepository.findById(remainId)
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        log.info("빈자리 발생 이벤트 수신: remainId = {}, 현재 잔여 좌석 = {}", remainId, storeRemain.getRemainTeam());

        if (storeRemain.getRemainTeam() <= 0) {
            log.warn("잔여 좌석이 0 이하이므로 알림을 발송하지 않습니다.");
            return;
        }

        List<Vacancy> subscribers = vacancyRepository.findWithUserByStoreRemain_IdAndStatusAndIsDeletedFalse(
                remainId, VacancyStatus.ACTIVE);

        if (subscribers.isEmpty()) {
            log.info("해당 시간대에 대한 빈자리 알림 구독자가 없습니다.");
            return;
        }

        String storeName = storeRemain.getStore().getStoreName();
        String remainDate = storeRemain.getRemainDate().toString();
        String remainTime = storeRemain.getRemainTime().toString();

        log.info("{}명에게 알림 생성을 시작합니다.", subscribers.size());

        for (Vacancy vacancy : subscribers) {
            User user = vacancy.getUser();
            if (user == null) continue;

            String title = "빈자리 알림";
            String content = String.format("%s %s %s에 빈자리가 발생했습니다! 지금 바로 예약하세요.",
                    storeName, remainDate, remainTime);

            notificationService.createNotification(user, NotificationType.VACANCY, title, content, storeRemain.getStore().getId());
        }
    }
}
