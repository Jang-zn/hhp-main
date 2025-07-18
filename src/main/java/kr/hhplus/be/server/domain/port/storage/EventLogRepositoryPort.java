package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.EventLog;

import java.util.List;

public interface EventLogRepositoryPort {
    EventLog save(EventLog eventLog);
    List<EventLog> findByStatus(String status);
    List<EventLog> findByEventType(String eventType);
} 