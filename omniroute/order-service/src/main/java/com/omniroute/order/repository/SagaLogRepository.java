package com.omniroute.order.repository;

import com.omniroute.order.domain.SagaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaLogRepository extends JpaRepository<SagaLog, Long> {

    Optional<SagaLog> findByEventId(String eventId);

    List<SagaLog> findByOrderIdOrderByCreatedAtAsc(String orderId);

    boolean existsByEventId(String eventId);
}
