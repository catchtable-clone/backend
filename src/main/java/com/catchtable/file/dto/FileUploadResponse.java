package com.catchtable.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "파일 업로드 응답")
public record FileUploadResponse(
        @Schema(description = "업로드된 파일에 접근 가능한 URL", example = "http://localhost:8080/uploads/abc-pasta.jpg")
        String url
) {
    public static FileUploadResponse of(String url) {
        return new FileUploadResponse(url);
    }
}
