package com.catchtable.store.dto.status;

import com.catchtable.store.entity.StoreStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class StoreStatusUpdateRequest {

    @NotNull
    private StoreStatus status;
}
