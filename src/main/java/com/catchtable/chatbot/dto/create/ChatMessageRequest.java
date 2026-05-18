package com.catchtable.chatbot.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(

        @Schema(example = "모수 용산점 3월 22일 18시 3명 예약해줘")
        @NotBlank(message = "메시지는 필수입니다.")
        @Size(max = 500, message = "메시지는 500자 이하여야 합니다.")
        String message,

        @Schema(description = "사용자 현재 위도 (선택)", example = "37.5665")
        Double latitude,

        @Schema(description = "사용자 현재 경도 (선택)", example = "126.9780")
        Double longitude
) {
}
