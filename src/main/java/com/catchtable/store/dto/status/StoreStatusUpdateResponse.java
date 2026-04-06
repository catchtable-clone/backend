package com.catchtable.store.dto.status;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreStatusUpdateResponse {

    private Long storeId;
    private String status;

    public static StoreStatusUpdateResponse from(Long storeId, String status) {
        return new StoreStatusUpdateResponse(storeId, status);
    }
}
