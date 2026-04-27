package com.catchtable.coupon.repository;

import com.catchtable.coupon.entity.CouponTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ct FROM CouponTemplate ct WHERE ct.id = :id AND ct.isDeleted = false")
    Optional<CouponTemplate> findByIdWithLock(@Param("id") Long id);
}
