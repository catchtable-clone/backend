package com.catchtable.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.user.entity.User;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByUser(User user);

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

    /**
     * NOSHOW 자동 전환 대상 조회.
     * status = CONFIRMED 이고, 예약 시각(remainDate + remainTime)이 기준 시각(now - 30분) 이전인 건.
     * remainDate/remainTime이 분리 저장이라 OR 조건으로 비교한다.
     */
    @Query("SELECT r FROM Reservation r JOIN FETCH r.user u JOIN FETCH r.storeRemain sr JOIN FETCH sr.store s " +
            "WHERE r.status = :status " +
            "AND (sr.remainDate < :date " +
            "     OR (sr.remainDate = :date AND sr.remainTime <= :time))")
    List<Reservation> findNoshowTargets(
            @Param("status") ReservationStatus status,
            @Param("date") LocalDate date,
            @Param("time") LocalTime time);
}
