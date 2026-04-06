package com.catchtable.store.dto.update;

import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class StoreUpdateRequest {

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
}
