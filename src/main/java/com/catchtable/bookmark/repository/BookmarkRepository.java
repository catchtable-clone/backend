package com.catchtable.bookmark.repository;

import com.catchtable.bookmark.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.store WHERE b.folder.id = :folderId AND b.isDeleted = false")
    List<Bookmark> findByFolderIdAndIsDeletedFalse(@Param("folderId") Long folderId);

    // 폴더 삭제 시 벌크 소프트 삭제 — forEach UPDATE N번 → UPDATE 1번
    @Modifying
    @Query("UPDATE Bookmark b SET b.isDeleted = true WHERE b.folder.id = :folderId AND b.isDeleted = false")
    void softDeleteAllByFolderId(@Param("folderId") Long folderId);

    Optional<Bookmark> findByIdAndIsDeletedFalse(Long id);

    boolean existsByFolder_IdAndStore_IdAndIsDeletedFalse(Long folderId, Long storeId);
}
