package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;

import java.util.List;

public interface EventLogRepositoryPort {
    EventLog save(EventLog eventLog);
    List<EventLog> findByStatus(EventStatus status);
    List<EventLog> findByEventType(EventType eventType);
} 