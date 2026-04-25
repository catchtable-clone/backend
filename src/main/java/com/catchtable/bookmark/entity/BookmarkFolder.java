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

    public void softDelete() {
        this.isDeleted = true;
    }
}
