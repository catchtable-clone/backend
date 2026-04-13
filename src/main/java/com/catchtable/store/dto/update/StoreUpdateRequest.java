package com.catchtable.store.dto.update;

import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StoreUpdateRequest(
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
}
