package com.catchtable.remain.repository;

import com.catchtable.remain.entity.StoreRemain;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreRemainRepository extends JpaRepository<StoreRemain, Long> {

    /**
     * 🚨 [중요] 동시성 제어가 필요한 조회 (Optimistic Lock)
     * 예약을 생성하거나 변경할 때, 잔여 팀 수를 감소시키기 위해 해당 예약 시간대의 재고를 조회합니다.
     * @Lock(LockModeType.OPTIMISTIC)을 명시적으로 사용할 수도 있으나,
     * Entity에 @Version이 있다면 JPA가 기본적으로 UPDATE 시 Optimistic Lock을 적용합니다.
     * 하지만 락 강제(Force)나 의도 명확화를 위해 추가해둘 수 있습니다.
     * (여기서는 @Version에 위임하고 생략하거나, 추가하여 명시성을 높입니다.)
     */
    // @Lock(LockModeType.OPTIMISTIC)
    // Optional<StoreRemain> findById(Long id);

    /**
     * 예약 생성 시 (N+1 문제 방지용 Fetch Join)
     * 클라이언트가 예약 화면에서 Store(매장) 정보와 StoreRemain(시간대별 재고) 정보를
     * 동시에 요구할 가능성이 높으므로, 예약 검증 시 Fetch Join을 통해 한 번에 조회합니다.
     */
    @Query("SELECT sr FROM StoreRemain sr JOIN FETCH sr.store WHERE sr.id = :id")
    Optional<StoreRemain> findByIdWithStore(@Param("id") Long id);
}
