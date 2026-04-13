package com.catchtable.store.dto.status;

public record StoreStatusUpdateResponse(Long storeId, String status) {
    public static StoreStatusUpdateResponse from(Long storeId, String status) {
        return new StoreStatusUpdateResponse(storeId, status);
    }
}
