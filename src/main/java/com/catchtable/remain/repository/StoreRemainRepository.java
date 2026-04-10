package com.catchtable.remain.repository;

import com.catchtable.remain.entity.StoreRemain;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreRemainRepository extends JpaRepository<StoreRemain, Long> {

    @Query("SELECT sr FROM StoreRemain sr JOIN FETCH sr.store WHERE sr.id = :id")
    Optional<StoreRemain> findByIdWithStore(@Param("id") Long id);
}
