package com.omniroute.order.repository;

import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findAllByOrderByCreatedAtDesc();

    @Query("SELECT o FROM Order o WHERE o.status IN :statuses AND o.updatedAt < :cutoff")
    List<Order> findStuckOrders(
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("cutoff") Instant cutoff
    );

    List<Order> findByStatus(OrderStatus status);

    long countByStatus(OrderStatus status);
}
