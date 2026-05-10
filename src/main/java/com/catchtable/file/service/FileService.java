package com.catchtable.file.service;

import com.catchtable.file.dto.FileUploadResponse;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final String UPLOAD_DIR = "uploads";
    private static final String BASE_URL = "http://localhost:8080";
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Set<String> ALLOWED_TYPES = Set.of("store", "menu");

    public FileUploadResponse upload(MultipartFile file, String type, Long storeId) {
        validate(file);

        String extension = extractExtension(file.getOriginalFilename());
        String savedFilename = UUID.randomUUID() + "." + extension;
        String subPath = computeSubPath(type, storeId);

        try {
            Path uploadRoot = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            Path uploadPath = uploadRoot.resolve(subPath).normalize();
            // 경로 트래버설 방지: 최종 경로가 반드시 uploadRoot 하위에 있어야 함
            if (!uploadPath.startsWith(uploadRoot)) {
                throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
            }
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path destination = uploadPath.resolve(savedFilename).normalize();
            if (!destination.startsWith(uploadRoot)) {
                throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
            }
            file.transferTo(destination);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String url = BASE_URL + "/" + UPLOAD_DIR + "/" + subPath + "/" + savedFilename;
        return FileUploadResponse.of(url);
    }

    /**
     * 업로드 대상별 하위 경로 결정 (allowlist 기반)
     * - type=store + storeId → stores/{storeId}/store
     * - type=menu  + storeId → stores/{storeId}/menus
     * - 그 외(허용되지 않은 type, storeId 누락) → misc
     */
    private String computeSubPath(String type, Long storeId) {
        // type이 allowlist 밖이면 임의 경로 생성을 막기 위해 misc로 격리
        String safeType = (type != null && ALLOWED_TYPES.contains(type.toLowerCase()))
                ? type.toLowerCase()
                : null;
        if (storeId == null || safeType == null) {
            return "misc";
        }
        return switch (safeType) {
            case "store" -> "stores/" + storeId + "/store";
            case "menu" -> "stores/" + storeId + "/menus";
            default -> "misc";
        };
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }
        String extension = extractExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.FILE_INVALID_TYPE);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
