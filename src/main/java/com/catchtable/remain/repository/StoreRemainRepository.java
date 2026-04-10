package com.catchtable.remain.repository;

import com.catchtable.remain.entity.StoreRemain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StoreRemainRepository extends JpaRepository<StoreRemain, Long> {

    @Query("SELECT sr FROM StoreRemain sr JOIN FETCH sr.store WHERE sr.id = :id")
    Optional<StoreRemain> findByIdWithStore(@Param("id") Long id);

    @Query("SELECT sr FROM StoreRemain sr WHERE sr.store.id = :storeId AND sr.remainDate = :date ORDER BY sr.remainTime ASC")
    List<StoreRemain> findAllByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);
}
