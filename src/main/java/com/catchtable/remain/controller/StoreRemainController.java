package com.catchtable.remain.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.remain.dto.create.StoreRemainCreateRequestDto;
import com.catchtable.remain.service.StoreRemainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/remains")
@RequiredArgsConstructor
public class StoreRemainController {

    private final StoreRemainService storeRemainService;

    //나중에 스케줄러
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Void>> generateMonthlyRemain(
            @Valid @RequestBody StoreRemainCreateRequestDto request
    ) {
        storeRemainService.generateMonthlyRemain(request);
        return ResponseEntity
                .status(SuccessCode.REMAIN_CREATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REMAIN_CREATE_SUCCESS));
    }
}
