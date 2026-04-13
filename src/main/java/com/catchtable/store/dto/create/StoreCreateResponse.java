package com.catchtable.store.dto.create;

public record StoreCreateResponse(Long storeId) {
    public static StoreCreateResponse from(Long storeId) {
        return new StoreCreateResponse(storeId);
    }
}
