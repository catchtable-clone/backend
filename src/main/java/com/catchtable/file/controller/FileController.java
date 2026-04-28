package com.catchtable.file.controller;

import com.catchtable.file.dto.FileUploadResponse;
import com.catchtable.file.service.FileService;
import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "파일 업로드", description = "이미지 등 파일 업로드 API")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "이미지 파일 업로드", description = "이미지 파일을 업로드하고 접근 가능한 URL을 반환합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @Parameter(description = "업로드할 이미지 파일 (jpg, png, webp / 최대 5MB)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        FileUploadResponse response = fileService.upload(file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.FILE_UPLOADED, response));
    }
}
