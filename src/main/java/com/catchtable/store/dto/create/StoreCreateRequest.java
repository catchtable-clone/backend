package com.catchtable.store.dto.create;

import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import com.catchtable.store.entity.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StoreCreateRequest(
        @NotBlank String storeName,
        String storeImage,
        @NotNull Category category,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotBlank String address,
        @NotNull District district,
        @NotNull Integer team,
        @NotBlank String openTime,
        @NotBlank String closeTime
) {
    public Store toEntity() {
        return Store.builder()
                .storeName(storeName)
                .storeImage(storeImage)
                .category(category)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .district(district)
                .team(team)
                .openTime(openTime)
                .closeTime(closeTime)
                .build();
    }
}
