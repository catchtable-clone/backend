package com.catchtable.bookmark.repository;

import com.catchtable.bookmark.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.store WHERE b.folder.id = :folderId AND b.isDeleted = false")
    List<Bookmark> findByFolderIdAndIsDeletedFalse(@Param("folderId") Long folderId);

    Optional<Bookmark> findByIdAndIsDeletedFalse(Long id);

    boolean existsByFolder_IdAndStore_IdAndIsDeletedFalse(Long folderId, Long storeId);
}
