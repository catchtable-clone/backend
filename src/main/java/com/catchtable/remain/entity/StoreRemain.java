package com.catchtable.remain.entity;

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

    /**
     * 🚨 [중요] 동시성 제어 (Optimistic Lock)
     * 여러 사용자가 동시에 동일한 예약 시간대(StoreRemain)의 remainTeam(잔여 팀 수)을
     * 차감하려고 시도할 때 발생하는 Race Condition(초과 예약)을 방지하기 위한 버전 관리 컬럼입니다.
     * JPA는 업데이트 시점의 버전이 읽어온 시점의 버전과 다르면 OptimisticLockException을 발생시킵니다.
     *
     * [이력서 포인트]
     * 1단계: Optimistic Lock으로 DB 락을 최소화하여 성능 유지 + 애플리케이션 레벨에서 예외 처리(재시도 로직 등)
     * 2단계(고도화 예정): 트래픽이 집중되는 선착순 이벤트나 피크 타임에는 DB 커넥션 부하가 커지므로,
     * 이를 Redis(Redisson, Lua 스크립트) 분산 락으로 마이그레이션하여 DB 부하를 줄이고 처리량을 극대화한 경험 어필.
     */
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

    /**
     * 예약 생성/변경 시 잔여 팀 수 차감
     * 🚨 [경고] DB 상태(잔여 수량)를 변경하는 핵심 로직입니다.
     * 호출하는 쪽(@Transactional Service)에서 이 메서드를 호출할 때
     * 반드시 Fetch Join으로 N+1을 방지하고, OptimisticLockException에 대한 예외 처리를 고려해야 합니다.
     */
    public void decreaseRemainTeam() {
        if (this.remainTeam <= 0) {
            throw new IllegalStateException("해당 시간대의 예약이 마감되었습니다.");
        }
        this.remainTeam--;
    }

    /**
     * 예약 취소/변경 시 잔여 팀 수 복구
     * 🚨 [경고] 취소 로직은 예약 상태 변경(CANCELED)과 함께 반드시 하나의 트랜잭션으로 원자성을 보장해야 합니다.
     */
    public void increaseRemainTeam() {
        this.remainTeam++;
    }
}
