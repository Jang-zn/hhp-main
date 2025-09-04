package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository
public interface EventLogRepositoryPort extends JpaRepository<EventLog, Long> {
    List<EventLog> findByStatus(EventStatus status);
    List<EventLog> findByEventType(EventType eventType);
    List<EventLog> findByCorrelationId(String correlationId);
    Optional<EventLog> findTopByStatusOrderByCreatedAtDesc(EventStatus status);
    Optional<EventLog> findTopByCorrelationIdAndStatusOrderByCreatedAtDesc(String correlationId, EventStatus status);
} 