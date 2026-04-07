package com.catchtable.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.user.entity.User;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByUser(User user);
}
