package com.catchtable.bookmark.controller;

import com.catchtable.bookmark.dto.bookmark.create.BookmarkCreateRequest;
import com.catchtable.bookmark.dto.bookmark.create.BookmarkCreateResponse;
import com.catchtable.bookmark.dto.bookmark.delete.BookmarkDeleteResponse;
import com.catchtable.bookmark.dto.bookmark.read.BookmarkListResponse;
import com.catchtable.bookmark.dto.folder.create.BookmarkFolderCreateRequest;
import com.catchtable.bookmark.dto.folder.create.BookmarkFolderCreateResponse;
import com.catchtable.bookmark.dto.folder.delete.BookmarkFolderDeleteResponse;
import com.catchtable.bookmark.dto.folder.read.BookmarkFolderListResponse;
import com.catchtable.bookmark.dto.folder.update.BookmarkFolderUpdateRequest;
import com.catchtable.bookmark.dto.folder.update.BookmarkFolderUpdateResponse;
import com.catchtable.bookmark.service.BookmarkService;
import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "북마크", description = "북마크 폴더 및 북마크 관련 API")
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "북마크 폴더 생성", description = "새로운 북마크 폴더를 생성합니다.")
    @PostMapping("/api/v1/bookmark-folders")
    public ResponseEntity<ApiResponse<BookmarkFolderCreateResponse>> createFolder(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody BookmarkFolderCreateRequest request) {
        BookmarkFolderCreateResponse response = bookmarkService.createFolder(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.BOOKMARK_FOLDER_CREATED, response));
    }

    @Operation(summary = "북마크 폴더 목록 조회", description = "해당 유저의 북마크 폴더 목록을 조회합니다.")
    @GetMapping("/api/v1/bookmark-folders")
    public ResponseEntity<ApiResponse<List<BookmarkFolderListResponse>>> getFolders(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId) {
        List<BookmarkFolderListResponse> response = bookmarkService.getFolders(userId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BOOKMARK_FOLDER_LIST_OK, response));
    }

    @Operation(summary = "북마크 폴더 이름 수정", description = "북마크 폴더 이름을 수정합니다.")
    @PatchMapping("/api/v1/bookmark-folders/{folderId}")
    public ResponseEntity<ApiResponse<BookmarkFolderUpdateResponse>> updateFolder(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "폴더 ID", example = "1") @PathVariable Long folderId,
            @Valid @RequestBody BookmarkFolderUpdateRequest request) {
        BookmarkFolderUpdateResponse response = bookmarkService.updateFolder(userId, folderId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BOOKMARK_FOLDER_UPDATED, response));
    }

    @Operation(summary = "북마크 폴더 삭제", description = "북마크 폴더를 삭제합니다. 폴더 안의 북마크도 함께 삭제됩니다.")
    @DeleteMapping("/api/v1/bookmark-folders/{folderId}")
    public ResponseEntity<ApiResponse<BookmarkFolderDeleteResponse>> deleteFolder(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "폴더 ID", example = "1") @PathVariable Long folderId) {
        BookmarkFolderDeleteResponse response = bookmarkService.deleteFolder(userId, folderId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BOOKMARK_FOLDER_DELETED, response));
    }

    @Operation(summary = "북마크 추가", description = "폴더에 매장을 북마크합니다.")
    @PostMapping("/api/v1/bookmark-folders/{folderId}/bookmarks")
    public ResponseEntity<ApiResponse<BookmarkCreateResponse>> addBookmark(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "폴더 ID", example = "1") @PathVariable Long folderId,
            @Valid @RequestBody BookmarkCreateRequest request) {
        BookmarkCreateResponse response = bookmarkService.addBookmark(userId, folderId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.BOOKMARK_CREATED, response));
    }

    @Operation(summary = "북마크 목록 조회", description = "폴더 내 북마크 목록을 조회합니다.")
    @GetMapping("/api/v1/bookmark-folders/{folderId}/bookmarks")
    public ResponseEntity<ApiResponse<List<BookmarkListResponse>>> getBookmarks(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "폴더 ID", example = "1") @PathVariable Long folderId) {
        List<BookmarkListResponse> response = bookmarkService.getBookmarks(userId, folderId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BOOKMARK_LIST_OK, response));
    }

    @Operation(summary = "북마크 삭제", description = "북마크를 삭제합니다.")
    @DeleteMapping("/api/v1/bookmarks/{bookmarkId}")
    public ResponseEntity<ApiResponse<BookmarkDeleteResponse>> deleteBookmark(
            @Parameter(description = "유저 ID", example = "1") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "북마크 ID", example = "1") @PathVariable Long bookmarkId) {
        BookmarkDeleteResponse response = bookmarkService.deleteBookmark(userId, bookmarkId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BOOKMARK_DELETED, response));
    }
}
