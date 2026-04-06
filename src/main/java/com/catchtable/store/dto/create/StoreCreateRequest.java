package com.catchtable.store.dto.create;

import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import com.catchtable.store.entity.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class StoreCreateRequest {

    @NotBlank
    private String storeName;

    private String storeImage;

    @NotNull
    private Category category;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    @NotBlank
    private String address;

    @NotNull
    private District district;

    @NotNull
    private Integer team;

    @NotBlank
    private String openTime;

    @NotBlank
    private String closeTime;

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
