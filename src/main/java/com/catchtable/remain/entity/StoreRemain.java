package com.catchtable.remain.entity;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "store_remain")
public class StoreRemain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "remain_date", nullable = false)
    private LocalDate remainDate;

    @Column(name = "remain_time", nullable = false)
    private LocalTime remainTime;

    @Column(name = "remain_team", nullable = false)
    private Integer remainTeam;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Builder
    public StoreRemain(Store store, LocalDate remainDate, LocalTime remainTime, Integer remainTeam) {
        this.store = store;
        this.remainDate = remainDate;
        this.remainTime = remainTime;
        this.remainTeam = remainTeam;
        this.isDeleted = false;
    }

    public void decreaseRemainTeam() {
        if (this.remainTeam <= 0) {
            throw new CustomException(ErrorCode.REMAIN_EXHAUSTED);
        }
        this.remainTeam--;
    }

    public void increaseRemainTeam() {
        this.remainTeam++;
    }
}
