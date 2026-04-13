package com.catchtable.store.dto.read;

import com.catchtable.remain.dto.read.RemainDateResponse;
import com.catchtable.store.entity.Store;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class StoreDetailResponse {

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
    private String status;
    private Integer reviewCount;
    private Integer bookmarkCount;
    private List<RemainDateResponse> remainDates;

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
                store.getReviewCount(),
                store.getBookmarkCount(),
                remainDates
        );
    }
}
