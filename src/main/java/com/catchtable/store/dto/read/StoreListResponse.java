package com.catchtable.store.dto.read;

import com.catchtable.store.entity.Store;

public record StoreListResponse(
        Long storeId,
        String storeName,
        String storeImage,
        String category,
        String address,
        String district,
        Double latitude,
        Double longitude,
        Double averageStar,
        Integer reviewCount
) {
    public static StoreListResponse from(Store store) {
        return new StoreListResponse(
                store.getId(),
                store.getStoreName(),
                store.getStoreImage(),
                store.getCategory().name(),
                store.getAddress(),
                store.getDistrict().name(),
                store.getLatitude(),
                store.getLongitude(),
                store.getAverageStar(),
                store.getReviewCount()
        );
    }
}
