package com.catchtable.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.catchtable.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}

