package com.catchtable.payment.repository;

import com.catchtable.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.reservation r
            JOIN FETCH r.user
            JOIN FETCH r.storeRemain sr
            JOIN FETCH sr.store
            WHERE p.orderId = :orderId
            """)
    Optional<Payment> findByOrderIdWithAllForUpdate(@Param("orderId") String orderId);

    Optional<Payment> findByReservation_Id(Long reservationId);
}
