package com.catchtable.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByUser(User user);

    // 결제 미완료 PENDING 예약 자동 정리 — 좌석 복원·payment 정리에 storeRemain까지 즉시 로딩
    @Query("SELECT r FROM Reservation r JOIN FETCH r.storeRemain " +
            "WHERE r.status = :status AND r.createdAt < :threshold")
    List<Reservation> findExpiredPending(
            @Param("status") ReservationStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.storeRemain sr JOIN FETCH r.user " +
            "WHERE r.status = :status " +
            "AND r.reminded = false " +
            "AND sr.remainDate = :date " +
            "AND sr.remainTime BETWEEN :from AND :to")
    List<Reservation> findReminderTargets(
            @Param("status") ReservationStatus status,
            @Param("date") LocalDate date,
            @Param("from") LocalTime from,
            @Param("to") LocalTime to);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.user u JOIN FETCH r.storeRemain sr JOIN FETCH sr.store s WHERE r.id = :id")
    Optional<Reservation> findByIdWithUserAndStoreRemainAndStore(@Param("id") Long id);
}
