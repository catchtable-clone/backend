package com.catchtable.store.dto.update;

import com.catchtable.store.entity.Store;

public record StoreUpdateResponse(
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
        String closeTime
) {
    public static StoreUpdateResponse from(Store store) {
        return new StoreUpdateResponse(
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
                store.getCloseTime()
        );
    }
}
