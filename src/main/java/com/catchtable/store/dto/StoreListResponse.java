package com.catchtable.store.dto;

import com.catchtable.store.entity.Store;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreListResponse {

    private Long storeId;
    private String storeName;
    private String category;
    private String district;
    private String address;
    private String storeImage;
    private Double latitude;
    private Double longitude;

    public static StoreListResponse from(Store store) {
        return new StoreListResponse(
                store.getId(),
                store.getStoreName(),
                store.getStoreImage(),
                store.getCategory().name(),
                store.getAddress(),
                store.getDistrict().name(),
                store.getLatitude(),
                store.getLongitude()
        );
    }
}
