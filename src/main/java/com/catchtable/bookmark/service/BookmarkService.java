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
import com.catchtable.global.exception.AccessDeniedException;
import com.catchtable.global.exception.ResourceNotFoundException;
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

    private void ensureDefaultFolder(User user) {
        if (!bookmarkFolderRepository.existsByUserIdAndFolderTypeAndIsDeletedFalse(user.getId(), FolderType.DEFAULT)) {
            BookmarkFolder defaultFolder = BookmarkFolder.builder()
                    .user(user)
                    .folderName("기본 폴더")
                    .folderType(FolderType.DEFAULT)
                    .build();
            bookmarkFolderRepository.save(defaultFolder);
        }
    }

    @Transactional
    public BookmarkFolderCreateResponse createFolder(Long userId, BookmarkFolderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));

        BookmarkFolder folder = BookmarkFolder.builder()
                .user(user)
                .folderName(request.folderName())
                .folderType(FolderType.CUSTOM)
                .build();

        BookmarkFolder saved = bookmarkFolderRepository.save(folder);
        return new BookmarkFolderCreateResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<BookmarkFolderListResponse> getFolders(Long userId) {
        return bookmarkFolderRepository.findByUserIdAndIsDeletedFalse(userId)
                .stream()
                .map(f -> new BookmarkFolderListResponse(f.getId(), f.getFolderName()))
                .toList();
    }

    @Transactional
    public BookmarkFolderUpdateResponse updateFolder(Long userId, Long folderId, BookmarkFolderUpdateRequest request) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("폴더를 찾을 수 없습니다."));

        if (!folder.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 폴더만 수정할 수 있습니다.");
        }

        folder.updateName(request.folderName());
        return new BookmarkFolderUpdateResponse(folder.getId(), folder.getFolderName());
    }

    @Transactional
    public BookmarkFolderDeleteResponse deleteFolder(Long userId, Long folderId) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("폴더를 찾을 수 없습니다."));

        if (!folder.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 폴더만 삭제할 수 있습니다.");
        }

        bookmarkRepository.findByFolderIdAndIsDeletedFalse(folderId)
                .forEach(Bookmark::softDelete);

        folder.softDelete();

        return new BookmarkFolderDeleteResponse(folderId, "폴더와 내부 즐겨찾기가 모두 삭제되었습니다.");
    }

    @Transactional
    public BookmarkCreateResponse addBookmark(Long userId, Long folderId, BookmarkCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));

        ensureDefaultFolder(user);

        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("폴더를 찾을 수 없습니다."));

        if (!folder.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 폴더에만 즐겨찾기를 추가할 수 있습니다.");
        }

        Store store = storeRepository.findById(request.storeId())
                .filter(s -> !s.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("매장을 찾을 수 없습니다."));

        if (bookmarkRepository.existsByFolder_IdAndStore_IdAndIsDeletedFalse(folderId, store.getId())) {
            throw new IllegalArgumentException("이미 해당 폴더에 저장된 매장입니다.");
        }

        Bookmark bookmark = Bookmark.builder()
                .folder(folder)
                .store(store)
                .build();

        Bookmark saved = bookmarkRepository.save(bookmark);
        return new BookmarkCreateResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<BookmarkListResponse> getBookmarks(Long userId, Long folderId) {
        BookmarkFolder folder = bookmarkFolderRepository.findByIdAndIsDeletedFalse(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("폴더를 찾을 수 없습니다."));

        if (!folder.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 폴더만 조회할 수 있습니다.");
        }

        return bookmarkRepository.findByFolderIdAndIsDeletedFalse(folderId)
                .stream()
                .filter(b -> !b.getStore().getIsDeleted())
                .map(b -> new BookmarkListResponse(b.getId(), b.getStore().getId(), b.getStore().getStoreName(), b.getStore().getStoreImage(), b.getStore().getCategory(), b.getStore().getAddress()))
                .toList();
    }

    @Transactional
    public BookmarkDeleteResponse deleteBookmark(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findByIdAndIsDeletedFalse(bookmarkId)
                .orElseThrow(() -> new ResourceNotFoundException("즐겨찾기를 찾을 수 없습니다."));

        if (!bookmark.getFolder().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 즐겨찾기만 삭제할 수 있습니다.");
        }

        bookmark.softDelete();
        return new BookmarkDeleteResponse(bookmarkId, "삭제 완료");
    }
}
