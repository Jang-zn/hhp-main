package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryEventLogRepository implements EventLogRepositoryPort {
    
    private final Map<Long, EventLog> eventLogs = new ConcurrentHashMap<>();
    
    @Override
    public EventLog save(EventLog eventLog) {
        eventLogs.put(eventLog.getId(), eventLog);
        return eventLog;
    }
    
    @Override
    public List<EventLog> findByStatus(String status) {
        // TODO: 상태별 이벤트 로그 조회 로직 구현
        return new ArrayList<>();
    }
    
    @Override
    public List<EventLog> findByEventType(String eventType) {
        // TODO: 이벤트 타입별 조회 로직 구현
        return new ArrayList<>();
    }
} 