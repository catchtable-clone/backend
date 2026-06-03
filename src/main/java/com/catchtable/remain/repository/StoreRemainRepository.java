package com.catchtable.remain.repository;

import com.catchtable.remain.dto.projection.StoreRemainTimeView;
import com.catchtable.remain.entity.StoreRemain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface StoreRemainRepository extends JpaRepository<StoreRemain, Long> {

    @Query("SELECT sr FROM StoreRemain sr JOIN FETCH sr.store WHERE sr.id = :id")
    Optional<StoreRemain> findByIdWithStore(@Param("id") Long id);

    @Query("SELECT sr FROM StoreRemain sr WHERE sr.store.id = :storeId AND sr.remainDate = :date ORDER BY sr.remainTime ASC")
    List<StoreRemain> findAllByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);

    @Query("SELECT sr.remainDate, SUM(sr.remainTeam) FROM StoreRemain sr WHERE sr.store.id = :storeId AND sr.remainDate >= :fromDate GROUP BY sr.remainDate ORDER BY sr.remainDate ASC")
    List<Object[]> findDateAvailabilityByStoreId(@Param("storeId") Long storeId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT sr FROM StoreRemain sr " +
           "JOIN sr.store s " +
            "WHERE s.storeName = :storeName " +
           "AND sr.remainDate = :date " +
           "AND sr.remainTime = :time")
    Optional<StoreRemain> findByStoreNameAndDateTime(
            @Param("storeName") String storeName,
            @Param("date") LocalDate date,
            @Param("time") LocalTime time
    );

    @Query("SELECT sr.store.id AS storeId, sr.remainTime AS remainTime FROM StoreRemain sr " +
           "WHERE sr.remainDate = :date AND sr.store.id IN :storeIds")
    List<StoreRemainTimeView> findStoreIdAndTimesByDateAndStoreIds(
            @Param("date") LocalDate date,
            @Param("storeIds") List<Long> storeIds);

    /**
     * 지난 날짜(remain_date < today)의 미참조 슬롯을 batchSize 단위로 물리 삭제한다.
     * reservation / vacancy_subscriptions(→ payment 체인 포함)가 참조하는 슬롯은 보존한다
     * — 참조 슬롯을 지우면 FK 위반이 나므로 NOT EXISTS로 제외한다.
     * 대량 삭제 시 WAL 폭증·롱 락을 막기 위해 LIMIT 배치로 나눠 반복 호출한다.
     *
     * @return 이번 배치에서 삭제된 행 수
     */
    @Modifying
    @Query(value = """
            DELETE FROM store_remain
            WHERE id IN (
                SELECT sr.id FROM store_remain sr
                WHERE sr.remain_date < :today
                  AND NOT EXISTS (SELECT 1 FROM reservations r WHERE r.remain_id = sr.id)
                  AND NOT EXISTS (SELECT 1 FROM vacancy_subscriptions v WHERE v.remain_id = sr.id)
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteUnreferencedPastSlots(@Param("today") LocalDate today, @Param("batchSize") int batchSize);
}
