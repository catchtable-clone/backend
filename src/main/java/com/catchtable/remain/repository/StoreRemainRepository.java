package com.catchtable.remain.repository;

import com.catchtable.remain.dto.projection.StoreRemainTimeView;
import com.catchtable.remain.entity.StoreRemain;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
