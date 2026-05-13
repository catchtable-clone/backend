package com.catchtable.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            "AND (sr.remainDate > :fromDate " +
            "     OR (sr.remainDate = :fromDate AND sr.remainTime >= :fromTime)) " +
            "AND (sr.remainDate < :toDate " +
            "     OR (sr.remainDate = :toDate AND sr.remainTime <= :toTime))")
    List<Reservation> findReminderTargets(
            @Param("status") ReservationStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("fromTime") LocalTime fromTime,
            @Param("toDate") LocalDate toDate,
            @Param("toTime") LocalTime toTime);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.user u JOIN FETCH r.storeRemain sr JOIN FETCH sr.store s WHERE r.id = :id")
    Optional<Reservation> findByIdWithUserAndStoreRemainAndStore(@Param("id") Long id);

    /**
     * CONFIRMED 상태이면서 예약 시각이 기준 시각 이전인 예약을 NOSHOW로 일괄 전환한다.
     * 벌크 UPDATE라 @PreUpdate가 호출되지 않으므로 updatedAt도 쿼리에서 직접 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r " +
            "SET r.status = com.catchtable.reservation.entity.ReservationStatus.NOSHOW, " +
            "    r.updatedAt = :now " +
            "WHERE r.status = com.catchtable.reservation.entity.ReservationStatus.CONFIRMED " +
            "AND (r.storeRemain.remainDate < :date " +
            "     OR (r.storeRemain.remainDate = :date AND r.storeRemain.remainTime <= :time))")
    int bulkTransitionToNoshow(
            @Param("date") LocalDate date,
            @Param("time") LocalTime time,
            @Param("now") LocalDateTime now);
}
