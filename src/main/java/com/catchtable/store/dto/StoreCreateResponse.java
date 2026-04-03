package com.catchtable.store.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreCreateResponse {

    private Long storeId;

    public static StoreCreateResponse from(Long storeId) {
        return new StoreCreateResponse(storeId);
    }
}
