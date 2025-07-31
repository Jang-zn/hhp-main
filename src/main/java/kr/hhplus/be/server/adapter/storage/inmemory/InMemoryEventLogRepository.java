package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.exception.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import kr.hhplus.be.server.domain.exception.CommonException;

@Repository
@Profile("test_inmemory")
public class InMemoryEventLogRepository implements EventLogRepositoryPort {
    
    private final Map<Long, EventLog> eventLogs = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    @Override
    public EventLog save(EventLog eventLog) {
        if (eventLog == null) {
            throw new CommonException.InvalidInput();
        }
        if (eventLog.getEventType() == null) {
            throw new CommonException.InvalidInput();
        }
        if (eventLog.getStatus() == null) {
            throw new CommonException.InvalidInput();
        }
        
        Long eventLogId = eventLog.getId() != null ? eventLog.getId() : nextId.getAndIncrement();
        
        EventLog savedEventLog = eventLogs.compute(eventLogId, (key, existingEventLog) -> {
            if (existingEventLog != null) {
                eventLog.onUpdate();
                eventLog.setId(existingEventLog.getId());
                eventLog.setCreatedAt(existingEventLog.getCreatedAt());
                return eventLog;
            } else {
                eventLog.onCreate();
                if (eventLog.getId() == null) {
                    eventLog.setId(eventLogId);
                }
                return eventLog;
            }
        });
        
        return savedEventLog;
    }
    
    @Override
    public List<EventLog> findByStatus(EventStatus status) {
        if (status == null) {
            throw new CommonException.InvalidInput();
        }
        return eventLogs.values().stream()
                .filter(eventLog -> eventLog.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EventLog> findByEventType(EventType eventType) {
        if (eventType == null) {
            throw new CommonException.InvalidInput();
        }
        return eventLogs.values().stream()
                .filter(eventLog -> eventLog.getEventType() == eventType)
                .collect(Collectors.toList());
    }
} 