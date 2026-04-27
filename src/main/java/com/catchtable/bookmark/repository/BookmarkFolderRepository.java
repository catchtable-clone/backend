package com.catchtable.bookmark.repository;

import com.catchtable.bookmark.entity.BookmarkFolder;
import com.catchtable.bookmark.entity.FolderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkFolderRepository extends JpaRepository<BookmarkFolder, Long> {

    List<BookmarkFolder> findByUserIdAndIsDeletedFalse(Long userId);

    Optional<BookmarkFolder> findByIdAndIsDeletedFalse(Long id);

    boolean existsByUserIdAndFolderTypeAndIsDeletedFalse(Long userId, FolderType folderType);
}
