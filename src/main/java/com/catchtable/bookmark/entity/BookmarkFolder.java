package com.catchtable.bookmark.entity;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookmark_folders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BookmarkFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "folder_name", nullable = false)
    private String folderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "folder_type", nullable = false)
    private FolderType folderType;

    /**
     * 폴더 표시 색상 (HEX). 기본값은 주황색.
     * 무지개 팔레트(빨/주/노/초/파/남/보 등)를 프론트가 골라서 전달한다.
     */
    @Column(name = "color", nullable = false, length = 20)
    @Builder.Default
    private String color = "#F97316";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    public void validateOwner(Long userId) {
        if (!this.user.getId().equals(userId)) {
            throw new CustomException(ErrorCode.BOOKMARK_FOLDER_NOT_OWNER);
        }
    }

    public void updateName(String folderName) {
        this.folderName = folderName;
    }

    /**
     * 폴더 이름·색상을 한 번에 갱신. null인 항목은 그대로 둔다.
     */
    public void update(String folderName, String color) {
        if (folderName != null) this.folderName = folderName;
        if (color != null) this.color = color;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
