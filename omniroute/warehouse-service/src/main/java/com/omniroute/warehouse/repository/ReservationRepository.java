package com.omniroute.warehouse.repository;

import com.omniroute.warehouse.domain.Reservation;
import com.omniroute.warehouse.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByOrderId(String orderId);

    List<Reservation> findByOrderIdAndStatus(String orderId, ReservationStatus status);

    boolean existsByOrderId(String orderId);
}
