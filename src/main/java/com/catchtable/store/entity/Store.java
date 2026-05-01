package com.catchtable.store.entity;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "store_image")
    private String storeImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private District district;

    @Column(nullable = false)
    private Integer team;

    @Column(name = "open_time", nullable = false)
    private String openTime;

    @Column(name = "close_time", nullable = false)
    private String closeTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StoreStatus status = StoreStatus.ACTIVE;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "average_star", nullable = false)
    @Builder.Default
    private Double averageStar = 0.0;

    @Column(name = "bookmark_count", nullable = false)
    @Builder.Default
    private Integer bookmarkCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    public void update(String storeName, String storeImage, Category category,
                       Double latitude, Double longitude, String address,
                       District district, Integer team, String openTime, String closeTime) {
        this.storeName = storeName;
        this.storeImage = storeImage;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.district = district;
        this.team = team;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public void updateAverageStar(Double newAverageStar) {
        this.averageStar = newAverageStar != null ? newAverageStar : 0.0;
    }

    public void changeStatus(StoreStatus newStatus) {
        if (this.status == StoreStatus.INACTIVE) {
            throw new CustomException(ErrorCode.INACTIVE_STORE);
        }
        if (this.status == newStatus) {
            throw new CustomException(ErrorCode.SAME_STATUS);
        }
        this.status = newStatus;
        if (newStatus == StoreStatus.INACTIVE) {
            this.isDeleted = true;
        }
    }
}
