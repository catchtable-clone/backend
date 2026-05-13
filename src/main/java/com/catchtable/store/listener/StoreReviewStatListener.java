package com.catchtable.store.listener;

import com.catchtable.review.event.ReviewCreatedEvent;
import com.catchtable.review.event.ReviewDeletedEvent;
import com.catchtable.review.event.ReviewUpdatedEvent;
import com.catchtable.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리뷰 도메인 이벤트를 구독해 매장의 평균 별점·리뷰 수를 비동기 갱신한다.
 *
 * - 리뷰 작성·수정·삭제 트랜잭션이 commit된 후에만 동작 (AFTER_COMMIT)
 * - @Async로 별도 스레드에서 실행 → 사용자 응답 지연 없음
 * - 갱신 실패가 리뷰 작성 자체를 실패시키지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreReviewStatListener {

    private final StoreService storeService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCreated(ReviewCreatedEvent event) {
        try {
            storeService.applyReviewCreated(event.storeId(), event.star());
        } catch (Exception e) {
            log.warn("매장 별점 갱신 실패(create): storeId={}, star={}",
                    event.storeId(), event.star(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewDeleted(ReviewDeletedEvent event) {
        try {
            storeService.applyReviewDeleted(event.storeId(), event.deletedStar());
        } catch (Exception e) {
            log.warn("매장 별점 갱신 실패(delete): storeId={}, deletedStar={}",
                    event.storeId(), event.deletedStar(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewUpdated(ReviewUpdatedEvent event) {
        try {
            storeService.applyReviewUpdated(event.storeId(), event.oldStar(), event.newStar());
        } catch (Exception e) {
            log.warn("매장 별점 갱신 실패(update): storeId={}, oldStar={}, newStar={}",
                    event.storeId(), event.oldStar(), event.newStar(), e);
        }
    }
}
