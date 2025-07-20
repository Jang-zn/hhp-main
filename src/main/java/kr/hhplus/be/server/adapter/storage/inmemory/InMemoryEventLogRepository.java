package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryEventLogRepository implements EventLogRepositoryPort {
    
    private final Map<Long, EventLog> eventLogs = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    @Override
    public EventLog save(EventLog eventLog) {
        if (eventLog == null) {
            throw new IllegalArgumentException("EventLog cannot be null");
        }
        if (eventLog.getEventType() == null) {
            throw new IllegalArgumentException("EventLog eventType cannot be null");
        }
        if (eventLog.getStatus() == null) {
            throw new IllegalArgumentException("EventLog status cannot be null");
        }
        
        Long eventLogId = eventLog.getId() != null ? eventLog.getId() : nextId.getAndIncrement();
        
        EventLog savedEventLog = eventLogs.compute(eventLogId, (key, existingEventLog) -> {
            if (existingEventLog != null) {
                return EventLog.builder()
                        .id(existingEventLog.getId())
                        .eventType(eventLog.getEventType())
                        .payload(eventLog.getPayload())
                        .status(eventLog.getStatus())
                        .createdAt(existingEventLog.getCreatedAt())
                        .updatedAt(eventLog.getUpdatedAt())
                        .build();
            } else {
                return EventLog.builder()
                        .id(eventLogId)
                        .eventType(eventLog.getEventType())
                        .payload(eventLog.getPayload())
                        .status(eventLog.getStatus())
                        .createdAt(eventLog.getCreatedAt())
                        .updatedAt(eventLog.getUpdatedAt())
                        .build();
            }
        });
        
        return savedEventLog;
    }
    
    @Override
    public List<EventLog> findByStatus(EventStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("EventStatus cannot be null");
        }
        return eventLogs.values().stream()
                .filter(eventLog -> eventLog.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EventLog> findByEventType(EventType eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("EventType cannot be null");
        }
        return eventLogs.values().stream()
                .filter(eventLog -> eventLog.getEventType() == eventType)
                .collect(Collectors.toList());
    }
} 