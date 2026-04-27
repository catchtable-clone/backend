package com.catchtable.vacancy.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record VacancyRegisterRequest(
    // TODO: userId는 인증 구현 후 SecurityContext에서 추출해야 함 (임시 처리)
    
    @Schema(example = "1") @NotNull Long userId,
    @Schema(example = "1") @NotNull Long remainId
) {}