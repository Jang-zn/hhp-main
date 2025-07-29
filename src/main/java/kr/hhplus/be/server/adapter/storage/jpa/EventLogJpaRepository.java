package kr.hhplus.be.server.adapter.storage.jpa;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;

@Repository
@Profile({"local", "test", "dev", "prod", "integration-test"})
@RequiredArgsConstructor
public class EventLogJpaRepository implements EventLogRepositoryPort {

    private final EntityManager entityManager;

    @Override
    public EventLog save(EventLog eventLog) {
        if (eventLog.getId() == null) {
            entityManager.persist(eventLog);
            return eventLog;
        } else {
            return entityManager.merge(eventLog);
        }
    }

    @Override
    public List<EventLog> findByStatus(EventStatus status) {
        return entityManager.createQuery(
            "SELECT e FROM EventLog e WHERE e.status = :status", EventLog.class)
            .setParameter("status", status)
            .getResultList();
    }

    @Override
    public List<EventLog> findByEventType(EventType eventType) {
        return entityManager.createQuery(
            "SELECT e FROM EventLog e WHERE e.eventType = :eventType", EventLog.class)
            .setParameter("eventType", eventType)
            .getResultList();
    }
}