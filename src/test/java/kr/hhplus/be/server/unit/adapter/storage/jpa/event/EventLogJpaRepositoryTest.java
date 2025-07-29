package kr.hhplus.be.server.unit.adapter.storage.jpa.event;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.EventLogJpaRepository;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("EventLogJpaRepository 단위 테스트")
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

    @Nested
    @DisplayName("이벤트 로그 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 이벤트 로그 저장")
        void save_NewEventLog_Success() {
            // given
            EventLog eventLog = EventLog.builder()
                    .eventType(EventType.ORDER_CREATED)
                    .status(EventStatus.PENDING)
                    .payload("test payload")
                    .createdAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(eventLog);

            // when
            EventLog savedEventLog = eventLogJpaRepository.save(eventLog);

            // then
            assertThat(savedEventLog).isEqualTo(eventLog);
            verify(entityManager, times(1)).persist(eventLog);
            verify(entityManager, never()).merge(any());
        }

        @Test
        @DisplayName("성공케이스: 기존 이벤트 로그 업데이트")
        void save_ExistingEventLog_Success() {
            // given
            EventLog eventLog = EventLog.builder()
                    .id(1L)
                    .eventType(EventType.PAYMENT_COMPLETED)
                    .status(EventStatus.PUBLISHED)
                    .payload("updated payload")
                    .build();

            when(entityManager.merge(eventLog)).thenReturn(eventLog);

            // when
            EventLog savedEventLog = eventLogJpaRepository.save(eventLog);

            // then
            assertThat(savedEventLog).isEqualTo(eventLog);
            verify(entityManager, times(1)).merge(eventLog);
            verify(entityManager, never()).persist(any());
        }

        @ParameterizedTest
        @EnumSource(EventType.class)
        @DisplayName("성공케이스: 다양한 이벤트 타입으로 저장")
        void save_WithDifferentEventTypes(EventType eventType) {
            // given
            EventLog eventLog = EventLog.builder()
                    .eventType(eventType)
                    .status(EventStatus.PENDING)
                    .payload("test")
                    .build();

            doNothing().when(entityManager).persist(eventLog);

            // when
            EventLog savedEventLog = eventLogJpaRepository.save(eventLog);

            // then
            assertThat(savedEventLog.getEventType()).isEqualTo(eventType);
            verify(entityManager, times(1)).persist(eventLog);
        }
    }

    @Nested
    @DisplayName("상태별 조회 테스트")
    class FindByStatusTests {

        @ParameterizedTest
        @EnumSource(EventStatus.class)
        @DisplayName("성공케이스: 상태별 이벤트 로그 조회")
        void findByStatus_Success(EventStatus status) {
            // given
            List<EventLog> expectedEventLogs = Arrays.asList(
                    EventLog.builder().id(1L).status(status).eventType(EventType.ORDER_CREATED).build(),
                    EventLog.builder().id(2L).status(status).eventType(EventType.PAYMENT_COMPLETED).build()
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
        @DisplayName("성공케이스: 해당 상태의 이벤트 로그가 없는 경우")
        void findByStatus_EmptyResult() {
            // given
            EventStatus status = EventStatus.PENDING;

            when(entityManager.createQuery(anyString(), eq(EventLog.class))).thenReturn(eventLogQuery);
            when(eventLogQuery.setParameter("status", status)).thenReturn(eventLogQuery);
            when(eventLogQuery.getResultList()).thenReturn(Arrays.asList());

            // when
            List<EventLog> eventLogs = eventLogJpaRepository.findByStatus(status);

            // then
            assertThat(eventLogs).isEmpty();
        }
    }

    @Nested
    @DisplayName("이벤트 타입별 조회 테스트")
    class FindByEventTypeTests {

        @ParameterizedTest
        @EnumSource(EventType.class)
        @DisplayName("성공케이스: 이벤트 타입별 로그 조회")
        void findByEventType_Success(EventType eventType) {
            // given
            List<EventLog> expectedEventLogs = Arrays.asList(
                    EventLog.builder().id(1L).eventType(eventType).status(EventStatus.PENDING).build(),
                    EventLog.builder().id(2L).eventType(eventType).status(EventStatus.PUBLISHED).build()
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
        @DisplayName("성공케이스: 해당 이벤트 타입의 로그가 없는 경우")
        void findByEventType_EmptyResult() {
            // given
            EventType eventType = EventType.ORDER_CREATED;

            when(entityManager.createQuery(anyString(), eq(EventLog.class))).thenReturn(eventLogQuery);
            when(eventLogQuery.setParameter("eventType", eventType)).thenReturn(eventLogQuery);
            when(eventLogQuery.getResultList()).thenReturn(Arrays.asList());

            // when
            List<EventLog> eventLogs = eventLogJpaRepository.findByEventType(eventType);

            // then
            assertThat(eventLogs).isEmpty();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: persist 중 예외 발생")
        void save_PersistException() {
            // given
            EventLog eventLog = EventLog.builder()
                    .eventType(EventType.ORDER_CREATED)
                    .status(EventStatus.PENDING)
                    .build();

            doThrow(new RuntimeException("DB 연결 실패")).when(entityManager).persist(eventLog);

            // when & then
            assertThatThrownBy(() -> eventLogJpaRepository.save(eventLog))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 연결 실패");
        }

        @Test
        @DisplayName("실패케이스: merge 중 예외 발생")
        void save_MergeException() {
            // given
            EventLog eventLog = EventLog.builder()
                    .id(1L)
                    .eventType(EventType.ORDER_CREATED)
                    .status(EventStatus.PENDING)
                    .build();

            when(entityManager.merge(eventLog)).thenThrow(new RuntimeException("트랜잭션 오류"));

            // when & then
            assertThatThrownBy(() -> eventLogJpaRepository.save(eventLog))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("트랜잭션 오류");
        }

        @Test
        @DisplayName("실패케이스: 상태별 조회 중 예외 발생")
        void findByStatus_QueryException() {
            // given
            EventStatus status = EventStatus.PENDING;

            when(entityManager.createQuery(anyString(), eq(EventLog.class)))
                    .thenThrow(new RuntimeException("쿼리 실행 오류"));

            // when & then
            assertThatThrownBy(() -> eventLogJpaRepository.findByStatus(status))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("쿼리 실행 오류");
        }

        @Test
        @DisplayName("실패케이스: 이벤트 타입별 조회 중 예외 발생")
        void findByEventType_QueryException() {
            // given
            EventType eventType = EventType.ORDER_CREATED;

            when(entityManager.createQuery(anyString(), eq(EventLog.class)))
                    .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

            // when & then
            assertThatThrownBy(() -> eventLogJpaRepository.findByEventType(eventType))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("데이터베이스 연결 오류");
        }
    }
}