package com.catchtable.remain.dto.read;

import java.time.LocalDate;

public record RemainDateResponse(
        LocalDate date,
        boolean available
) {
}
