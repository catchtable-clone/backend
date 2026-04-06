package com.catchtable.vacancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VacancyRegisterRequest {
    // TODO: userId는 인증 구현 후 SecurityContext에서 추출해야 함 (임시 처리)
    @Schema(example = "1")
    // 입력값 검증용
    @NotNull
    private Long userId;

    @Schema(example = "1")
    @NotNull
    private Long remainId;
}
