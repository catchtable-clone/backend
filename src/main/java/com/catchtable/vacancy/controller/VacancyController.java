package com.catchtable.vacancy.controller;

import com.catchtable.vacancy.dto.create.VacancyRegisterRequest;
import com.catchtable.vacancy.dto.create.VacancyRegisterResponse;
import com.catchtable.vacancy.dto.write.VacancyListResponse;
import com.catchtable.vacancy.service.VacancyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vacancy")
public class VacancyController {

    private final VacancyService vacancyService;

    @PostMapping
    public ResponseEntity<VacancyRegisterResponse> register(@RequestBody @Valid VacancyRegisterRequest request) {
        Long vacancyId = vacancyService.register(request.userId(), request.remainId());
        return ResponseEntity.status(201).body(new VacancyRegisterResponse(vacancyId));
    }

    // 해야할거: userId는 인증 구현 후 @AuthenticationPrincipal로 교체
    @GetMapping("/me")
    public ResponseEntity<List<VacancyListResponse>> getMyList(@RequestParam Long userId) {
        return ResponseEntity.ok(vacancyService.getMyList(userId));
    }

    @DeleteMapping("/{vacancyId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long vacancyId) {
        Long deletedId = vacancyService.delete(vacancyId);
        return ResponseEntity.ok(Map.of("vacancyId", deletedId, "message", "삭제 완료"));
    }
}
