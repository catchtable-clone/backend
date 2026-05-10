package com.catchtable.store.dto.read;

import com.catchtable.remain.dto.read.RemainDateResponse;
import com.catchtable.store.entity.Store;

import java.util.List;

public record StoreDetailResponse(
        Long storeId,
        String storeName,
        String storeImage,
        String category,
        String address,
        String district,
        Double latitude,
        Double longitude,
        Integer team,
        String openTime,
        String closeTime,
        String status,
        Double averageStar,
        Integer reviewCount,
        Integer bookmarkCount,
        List<RemainDateResponse> remainDates
) {
    public static StoreDetailResponse from(Store store, List<RemainDateResponse> remainDates) {
        return new StoreDetailResponse(
                store.getId(),
                store.getStoreName(),
                store.getStoreImage(),
                store.getCategory().name(),
                store.getAddress(),
                store.getDistrict().name(),
                store.getLatitude(),
                store.getLongitude(),
                store.getTeam(),
                store.getOpenTime(),
                store.getCloseTime(),
                store.getStatus().name(),
                store.getAverageStar(),
                store.getReviewCount(),
                store.getBookmarkCount(),
                remainDates
        );
    }
}
