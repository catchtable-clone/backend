package com.catchtable.vacancy.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record VacancyRegisterRequest(
    @Schema(example = "1") @NotNull Long remainId
) {}