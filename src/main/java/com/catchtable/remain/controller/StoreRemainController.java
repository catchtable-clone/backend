package com.catchtable.remain.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.remain.dto.read.StoreRemainResponseDto;
import com.catchtable.remain.dto.create.StoreRemainCreateRequestDto;
import com.catchtable.remain.service.StoreRemainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/remains")
@RequiredArgsConstructor
public class StoreRemainController {

    private final StoreRemainService storeRemainService;

//    스케줄러 도입 후 사용 안 함. 기존 swagger 에서 수동으로 생성하던 POST /api/v1/remains 주석 처리.
//    @PostMapping
//    public ResponseEntity<ApiResponse<Void>> generateMonthlyRemain(
//            @Valid @RequestBody StoreRemainCreateRequestDto request
//    ) {
//        storeRemainService.generateMonthlyRemain(request);
//        return ResponseEntity
//                .status(SuccessCode.REMAIN_CREATE_SUCCESS.getHttpStatus())
//                .body(ApiResponse.success(SuccessCode.REMAIN_CREATE_SUCCESS));
//    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreRemainResponseDto>>> getStoreRemains(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {
        List<StoreRemainResponseDto> responseData = storeRemainService.getStoreRemains(storeId, date);
        return ResponseEntity
                .status(SuccessCode.REMAIN_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REMAIN_LOOKUP_SUCCESS, responseData));
    }
}
