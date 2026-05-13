package com.catchtable.review.event;

/**
 * 리뷰 별점 변경 후 발행. 매장 평균 별점 비동기 재계산용.
 */
public record ReviewUpdatedEvent(Long storeId, int oldStar, int newStar) {
}
