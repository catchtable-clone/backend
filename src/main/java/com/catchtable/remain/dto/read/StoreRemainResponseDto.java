package com.catchtable.remain.dto.read;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record StoreRemainResponseDto(
        Long remainId,
        LocalDate remainDate,
        @JsonFormat(pattern = "HH:mm")
        LocalTime remainTime,
        Integer remainTeam
) {
}
