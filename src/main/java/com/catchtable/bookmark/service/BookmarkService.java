package com.catchtable.bookmark.service;

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
import com.catchtable.bookmark.entity.Bookmark;
import com.catchtable.bookmark.entity.BookmarkFolder;
import com.catchtable.bookmark.entity.FolderType;
import com.catchtable.bookmark.repository.BookmarkFolderRepository;
import com.catchtable.bookmark.repository.BookmarkRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkFolderRepository bookmarkFolderRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    // 북마크 폴더 생성
    @Transactional
    public BookmarkFolderCreateResponse createFolder(Long userId, BookmarkFolderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        BookmarkFolder folder = BookmarkFolder.builder()
                .user(user)
                .folderName(request.folderName())
                .folderType(FolderType.CUSTOM)
                .build();

        BookmarkFolder saved = bookmarkFolderRepository.save(folder);
        return new BookmarkFolderCreateResponse(saved.getId());
    }

    // 북마크 폴더 목록 조회
    @Transactional(readOnly = true)
    public List<BookmarkFolderListResponse> getFolders(Long userId) {
        return bookmarkFolderRepository.findByUserIdAndIsDeletedFalse(userId)
                .stream()
                .map(f -> new BookmarkFolderListResponse(f.getId(), f.getFolderName()))
                .toList();
    }

    // 북마크 폴더 이름 수정
    @Transactional
    public BookmarkFolderUpdateResponse updateFolder(Long userId, Long folderId, BookmarkFolderUpdateRequest request) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_FOLDER_NOT_FOUND));

        folder.validateOwner(userId);

        if (folder.getFolderType() == FolderType.DEFAULT) {
            throw new CustomException(ErrorCode.BOOKMARK_DEFAULT_FOLDER_IMMUTABLE);
        }

        folder.updateName(request.folderName());
        return new BookmarkFolderUpdateResponse(folder.getId(), folder.getFolderName());
    }

    // 북마크 폴더 삭제
    @Transactional
    public BookmarkFolderDeleteResponse deleteFolder(Long userId, Long folderId) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_FOLDER_NOT_FOUND));

        folder.validateOwner(userId);

        if (folder.getFolderType() == FolderType.DEFAULT) {
            throw new CustomException(ErrorCode.BOOKMARK_DEFAULT_FOLDER_IMMUTABLE);
        }

        bookmarkRepository.findByFolderIdAndIsDeletedFalse(folderId)
                .forEach(Bookmark::softDelete);

        folder.softDelete();

        return new BookmarkFolderDeleteResponse(folderId, "폴더와 내부 즐겨찾기가 모두 삭제되었습니다.");
    }

    // 북마크 추가
    @Transactional
    public BookmarkCreateResponse addBookmark(Long userId, Long folderId, BookmarkCreateRequest request) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_FOLDER_NOT_FOUND));

        folder.validateOwner(userId);

        Store store = storeRepository.findById(request.storeId())
                .filter(s -> !s.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        if (bookmarkRepository.existsByFolder_IdAndStore_IdAndIsDeletedFalse(folderId, store.getId())) {
            throw new CustomException(ErrorCode.BOOKMARK_DUPLICATE);
        }

        Bookmark bookmark = Bookmark.builder()
                .folder(folder)
                .store(store)
                .build();

        Bookmark saved = bookmarkRepository.save(bookmark);
        return new BookmarkCreateResponse(saved.getId());
    }

    // 북마크 목록 조회
    @Transactional(readOnly = true)
    public List<BookmarkListResponse> getBookmarks(Long userId, Long folderId) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_FOLDER_NOT_FOUND));

        folder.validateOwner(userId);

        return bookmarkRepository.findByFolderIdAndIsDeletedFalse(folderId)
                .stream()
                .filter(b -> !b.getStore().getIsDeleted())
                .map(b -> new BookmarkListResponse(b.getId(), b.getStore().getId(), b.getStore().getStoreName(), b.getStore().getStoreImage(), b.getStore().getCategory(), b.getStore().getAddress()))
                .toList();
    }

    // 북마크 삭제
    @Transactional
    public BookmarkDeleteResponse deleteBookmark(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findByIdAndIsDeletedFalse(bookmarkId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_NOT_FOUND));

        bookmark.getFolder().validateOwner(userId);

        bookmark.softDelete();
        return new BookmarkDeleteResponse(bookmarkId, "삭제 완료");
    }
}
