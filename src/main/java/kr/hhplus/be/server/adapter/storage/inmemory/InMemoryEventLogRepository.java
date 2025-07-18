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
    
    /**
     * Saves the given EventLog in memory and returns the saved instance.
     *
     * If an EventLog with the same ID already exists, it is replaced.
     *
     * @param eventLog the EventLog to be saved
     * @return the saved EventLog
     */
    @Override
    public EventLog save(EventLog eventLog) {
        eventLogs.put(eventLog.getId(), eventLog);
        return eventLog;
    }
    
    /**
     * Returns a list of event logs that match the specified status.
     *
     * @param status the status to filter event logs by
     * @return a list of event logs with the given status; currently always returns an empty list
     */
    @Override
    public List<EventLog> findByStatus(String status) {
        // TODO: 상태별 이벤트 로그 조회 로직 구현
        return new ArrayList<>();
    }
    
    /**
     * Returns a list of event logs filtered by the specified event type.
     *
     * @param eventType the event type to filter by
     * @return a list of event logs matching the event type, or an empty list if none found
     */
    @Override
    public List<EventLog> findByEventType(String eventType) {
        // TODO: 이벤트 타입별 조회 로직 구현
        return new ArrayList<>();
    }
} 