package com.catchtable.vacancy.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import com.catchtable.vacancy.dto.create.VacancyRegisterRequest;
import com.catchtable.vacancy.dto.create.VacancyRegisterResponse;
import com.catchtable.vacancy.dto.write.VacancyListResponse;
import com.catchtable.vacancy.service.VacancyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vacancy")
public class VacancyController {

    private final VacancyService vacancyService;

    @PostMapping
    public ResponseEntity<ApiResponse<VacancyRegisterResponse>> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid VacancyRegisterRequest request) {
        Long vacancyId = vacancyService.register(userDetails.getUserId(), request.remainId());
        VacancyRegisterResponse responseData = new VacancyRegisterResponse(vacancyId);
        return ResponseEntity
                .status(SuccessCode.VACANCY_REGISTER_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.VACANCY_REGISTER_SUCCESS, responseData));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<VacancyListResponse>>> getMyList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<VacancyListResponse> responseData = vacancyService.getMyList(userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.VACANCY_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.VACANCY_LOOKUP_SUCCESS, responseData));
    }

    @DeleteMapping("/{vacancyId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long vacancyId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        vacancyService.delete(vacancyId, userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.VACANCY_DELETE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.VACANCY_DELETE_SUCCESS));
    }
}
