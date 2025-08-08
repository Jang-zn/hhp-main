package kr.hhplus.be.server.unit.adapter.storage.jpa.event;

import kr.hhplus.be.server.adapter.storage.jpa.EventLogJpaRepository;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
/**
 * EventLogJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: 이벤트 로그 데이터 저장소의 비즈니스 로직과 데이터 무결성 보장 검증
 * How: 이벤트 발생 및 추적 시나리오를 반영한 JPA 저장소 테스트로 구성
 */
@DisplayName("이벤트 로그 데이터 저장소 비즈니스 시나리오")
class EventLogJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<EventLog> eventLogQuery;

    private EventLogJpaRepository eventLogJpaRepository;

    @BeforeEach
    void setUp() {
        eventLogJpaRepository = new EventLogJpaRepository(entityManager);
    }

    @Test
    @DisplayName("새로운 비즈니스 이벤트 로그를 성공적으로 저장한다")
    void save_NewEventLog_Success() {
        // given - 새로운 주문 생성 이벤트 발생
        EventLog newEventLog = TestBuilder.EventLogBuilder.pendingEvent()
                .eventType(EventType.ORDER_CREATED)
                .payload("{\"키\":\"ord_123\", \"주문금액\":50000}")
                .build();

        doNothing().when(entityManager).persist(newEventLog);

        // when
        EventLog savedEventLog = eventLogJpaRepository.save(newEventLog);

        // then
        assertThat(savedEventLog).isEqualTo(newEventLog);
        verify(entityManager, times(1)).persist(newEventLog);
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("기존 이벤트 로그의 상태를 성공적으로 업데이트한다")
    void save_ExistingEventLog_Success() {
        // given - 결제 완료 이벤트의 발행 완료 처리
        EventLog completedEventLog = TestBuilder.EventLogBuilder.publishedEvent()
                .id(1L)
                .eventType(EventType.PAYMENT_COMPLETED)
                .payload("{\"paymentId\":1, \"amount\":50000}")
                .build();

        when(entityManager.merge(completedEventLog)).thenReturn(completedEventLog);

        // when
        EventLog savedEventLog = eventLogJpaRepository.save(completedEventLog);

        // then
        assertThat(savedEventLog).isEqualTo(completedEventLog);
        verify(entityManager, times(1)).merge(completedEventLog);
        verify(entityManager, never()).persist(any());
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("모든 비즈니스 이벤트 타입에 대해 로그 저장이 성공한다")
    void save_WithDifferentEventTypes(EventType eventType) {
        // given - 다양한 비즈니스 이벤트 발생
        EventLog businessEventLog = TestBuilder.EventLogBuilder.pendingEvent()
                .eventType(eventType)
                .payload("{\"eventData\":\"test_" + eventType.name() + "\"}")
                .build();

        doNothing().when(entityManager).persist(businessEventLog);

        // when
        EventLog savedEventLog = eventLogJpaRepository.save(businessEventLog);

        // then
        assertThat(savedEventLog.getEventType()).isEqualTo(eventType);
        verify(entityManager, times(1)).persist(businessEventLog);
    }

    @ParameterizedTest
    @EnumSource(EventStatus.class)
    @DisplayName("이벤트 로그 상태별 조회가 성공한다")
    void findByStatus_Success(EventStatus status) {
        // given - 특정 상태의 비즈니스 이벤트 로그들
        List<EventLog> expectedEventLogs = Arrays.asList(
                TestBuilder.EventLogBuilder.defaultEvent().id(1L).status(status).eventType(EventType.ORDER_CREATED).build(),
                TestBuilder.EventLogBuilder.defaultEvent().id(2L).status(status).eventType(EventType.PAYMENT_COMPLETED).build()
        );

        when(entityManager.createQuery(anyString(), eq(EventLog.class))).thenReturn(eventLogQuery);
        when(eventLogQuery.setParameter("status", status)).thenReturn(eventLogQuery);
        when(eventLogQuery.getResultList()).thenReturn(expectedEventLogs);

        // when
        List<EventLog> eventLogs = eventLogJpaRepository.findByStatus(status);

        // then
        assertThat(eventLogs).hasSize(2);
        assertThat(eventLogs).allMatch(log -> log.getStatus() == status);
        verify(entityManager).createQuery("SELECT e FROM EventLog e WHERE e.status = :status", EventLog.class);
    }

    @Test
    @DisplayName("해당 상태의 이벤트 로그가 없을 때 빈 목록을 반환한다")
    void findByStatus_EmptyResult() {
        // given - 아직 처리되지 않은 이벤트가 없는 상황
        EventStatus noPendingStatus = EventStatus.PENDING;

        when(entityManager.createQuery(anyString(), eq(EventLog.class))).thenReturn(eventLogQuery);
        when(eventLogQuery.setParameter("status", noPendingStatus)).thenReturn(eventLogQuery);
        when(eventLogQuery.getResultList()).thenReturn(Arrays.asList());

        // when
        List<EventLog> eventLogs = eventLogJpaRepository.findByStatus(noPendingStatus);

        // then
        assertThat(eventLogs).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("비즈니스 이벤트 타입별 로그 조회가 성공한다")
    void findByEventType_Success(EventType eventType) {
        // given - 특정 비즈니스 이벤트 타입의 로그들
        List<EventLog> expectedEventLogs = Arrays.asList(
                TestBuilder.EventLogBuilder.pendingEvent().id(1L).eventType(eventType).build(),
                TestBuilder.EventLogBuilder.publishedEvent().id(2L).eventType(eventType).build()
        );

        when(entityManager.createQuery(anyString(), eq(EventLog.class))).thenReturn(eventLogQuery);
        when(eventLogQuery.setParameter("eventType", eventType)).thenReturn(eventLogQuery);
        when(eventLogQuery.getResultList()).thenReturn(expectedEventLogs);

        // when
        List<EventLog> eventLogs = eventLogJpaRepository.findByEventType(eventType);

        // then
        assertThat(eventLogs).hasSize(2);
        assertThat(eventLogs).allMatch(log -> log.getEventType() == eventType);
        verify(entityManager).createQuery("SELECT e FROM EventLog e WHERE e.eventType = :eventType", EventLog.class);
    }

    @Test
    @DisplayName("해당 이벤트 타입의 로그가 없을 때 빈 목록을 반환한다")
    void findByEventType_EmptyResult() {
        // given - 주문 생성 이벤트가 아직 발생하지 않은 상황
        EventType noOrderEventType = EventType.ORDER_CREATED;

        when(entityManager.createQuery(anyString(), eq(EventLog.class))).thenReturn(eventLogQuery);
        when(eventLogQuery.setParameter("eventType", noOrderEventType)).thenReturn(eventLogQuery);
        when(eventLogQuery.getResultList()).thenReturn(Arrays.asList());

        // when
        List<EventLog> eventLogs = eventLogJpaRepository.findByEventType(noOrderEventType);

        // then
        assertThat(eventLogs).isEmpty();
    }

    @Test
    @DisplayName("이벤트 로그 저장 중 데이터베이스 오류 시 예외가 전파된다")
    void save_PersistException() {
        // given - 데이터베이스 연결 오류 상황
        EventLog businessEventLog = TestBuilder.EventLogBuilder.pendingEvent()
                .eventType(EventType.ORDER_CREATED)
                .build();

        doThrow(new RuntimeException("DB 연결 실패")).when(entityManager).persist(businessEventLog);

        // when & then
        assertThatThrownBy(() -> eventLogJpaRepository.save(businessEventLog))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 연결 실패");
    }

    @Test
    @DisplayName("이벤트 로그 업데이트 중 트랜잭션 오류 시 예외가 전파된다")
    void save_MergeException() {
        // given - 트랜잭션 충돌 상황
        EventLog existingEventLog = TestBuilder.EventLogBuilder.pendingEvent()
                .id(1L)
                .eventType(EventType.ORDER_CREATED)
                .build();

        when(entityManager.merge(existingEventLog)).thenThrow(new RuntimeException("트랜잭션 오류"));

        // when & then
        assertThatThrownBy(() -> eventLogJpaRepository.save(existingEventLog))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("트랜잭션 오류");
    }

    @Test
    @DisplayName("이벤트 로그 상태별 조회 중 쿼리 오류 시 예외가 전파된다")
    void findByStatus_QueryException() {
        // given - 데이터베이스 쿼리 오류 상황
        EventStatus targetStatus = EventStatus.PENDING;

        when(entityManager.createQuery(anyString(), eq(EventLog.class)))
                .thenThrow(new RuntimeException("쿼리 실행 오류"));

        // when & then
        assertThatThrownBy(() -> eventLogJpaRepository.findByStatus(targetStatus))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("쿼리 실행 오류");
    }

    @Test
    @DisplayName("이벤트 로그 타입별 조회 중 데이터베이스 오류 시 예외가 전파된다")
    void findByEventType_QueryException() {
        // given - 데이터베이스 연결 두절 상황
        EventType targetEventType = EventType.ORDER_CREATED;

        when(entityManager.createQuery(anyString(), eq(EventLog.class)))
                .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

        // when & then
        assertThatThrownBy(() -> eventLogJpaRepository.findByEventType(targetEventType))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("데이터베이스 연결 오류");
    }
}