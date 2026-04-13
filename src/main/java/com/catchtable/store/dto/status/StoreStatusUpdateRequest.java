package com.catchtable.store.dto.status;

import com.catchtable.store.entity.StoreStatus;
import jakarta.validation.constraints.NotNull;

public record StoreStatusUpdateRequest(
        @NotNull StoreStatus status
) {
}
