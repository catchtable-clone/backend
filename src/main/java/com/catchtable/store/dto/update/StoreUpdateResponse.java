package com.catchtable.store.dto.update;

import com.catchtable.store.entity.Store;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreUpdateResponse {

    private Long storeId;
    private String storeName;
    private String storeImage;
    private String category;
    private String address;
    private String district;
    private Double latitude;
    private Double longitude;
    private Integer team;
    private String openTime;
    private String closeTime;

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
