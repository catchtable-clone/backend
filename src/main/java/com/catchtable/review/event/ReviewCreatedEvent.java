package com.catchtable.review.event;

/**
 * 리뷰 생성 후 발행. 매장 평균 별점·리뷰 수 비동기 갱신용.
 */
public record ReviewCreatedEvent(Long storeId, int star) {
}
