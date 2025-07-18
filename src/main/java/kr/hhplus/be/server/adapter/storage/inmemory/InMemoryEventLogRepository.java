package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryEventLogRepository implements EventLogRepositoryPort {
    
    private final Map<Long, EventLog> eventLogs = new ConcurrentHashMap<>();
    
    @Override
    public EventLog save(EventLog eventLog) {
        eventLogs.put(eventLog.getId(), eventLog);
        return eventLog;
    }
    
    @Override
    public List<EventLog> findByStatus(EventStatus status) {
        return eventLogs.values().stream()
                .filter(eventLog -> eventLog.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EventLog> findByEventType(EventType eventType) {
        return eventLogs.values().stream()
                .filter(eventLog -> eventLog.getEventType() == eventType)
                .collect(Collectors.toList());
    }
} 