package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.EventLog;

import java.util.List;

public interface EventLogRepositoryPort {
    /**
 * Persists the given event log and returns the saved entity.
 *
 * @param eventLog the event log to be saved
 * @return the saved event log entity
 */
EventLog save(EventLog eventLog);
    /**
 * Retrieves a list of event logs that match the specified status.
 *
 * @param status the status to filter event logs by
 * @return a list of event logs with the given status
 */
List<EventLog> findByStatus(String status);
    /**
 * Retrieves a list of event logs that match the specified event type.
 *
 * @param eventType the type of event to filter by
 * @return a list of EventLog objects with the given event type
 */
List<EventLog> findByEventType(String eventType);
} 