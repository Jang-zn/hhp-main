package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryEventLogRepository;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryEventLogRepository 단위 테스트")
class InMemoryEventLogRepositoryTest {

    private InMemoryEventLogRepository eventLogRepository;

    @BeforeEach
    void setUp() {
        eventLogRepository = new InMemoryEventLogRepository();
    }

    @Nested
    @DisplayName("이벤트 로그 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 이벤트 로그 저장")
        void save_Success() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_CREATED)
                .payload("{\"orderId\": 1, \"userId\": 100}")
                .status(EventStatus.PENDING)
                .build();

        // when
        EventLog savedEventLog = eventLogRepository.save(eventLog);

        // then
        assertThat(savedEventLog).isNotNull();
        assertThat(savedEventLog.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(savedEventLog.getPayload()).isEqualTo("{\"orderId\": 1, \"userId\": 100}");
        assertThat(savedEventLog.getStatus()).isEqualTo(EventStatus.PENDING);
    }

        @Test
        @DisplayName("성공케이스: 결제 완료 이벤트 로그 저장")
        void save_PaymentCompleted() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.PAYMENT_COMPLETED)
                .payload("{\"paymentId\": 1, \"amount\": 100000}")
                .status(EventStatus.PUBLISHED)
                .build();

        // when
        EventLog savedEventLog = eventLogRepository.save(eventLog);

        // then
        assertThat(savedEventLog).isNotNull();
        assertThat(savedEventLog.getEventType()).isEqualTo(EventType.PAYMENT_COMPLETED);
            assertThat(savedEventLog.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryEventLogRepositoryTest#provideEventLogData")
        @DisplayName("성공케이스: 다양한 이벤트 로그 데이터로 저장")
        void save_WithDifferentEventData(EventType eventType, String payload, EventStatus status) {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(eventType)
                .payload(payload)
                .status(status)
                .build();

        // when
        EventLog savedEventLog = eventLogRepository.save(eventLog);

        // then
        assertThat(savedEventLog).isNotNull();
            assertThat(savedEventLog.getEventType()).isEqualTo(eventType);
            assertThat(savedEventLog.getPayload()).isEqualTo(payload);
            assertThat(savedEventLog.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("성공케이스: 빈 payload로 이벤트 로그 저장")
        void save_WithEmptyPayload() {
            // given
            EventLog eventLog = EventLog.builder()
                    .eventType(EventType.ORDER_CREATED)
                    .payload("")
                    .status(EventStatus.PENDING)
                    .build();

            // when
            EventLog savedEventLog = eventLogRepository.save(eventLog);

            // then
            assertThat(savedEventLog).isNotNull();
            assertThat(savedEventLog.getPayload()).isEmpty();
        }

        @Test
        @DisplayName("성공케이스: 긴 payload로 이벤트 로그 저장")
        void save_WithLongPayload() {
            // given
            String longPayload = "{".repeat(1000) + "\"data\": \"test\"" + "}".repeat(1000);
            EventLog eventLog = EventLog.builder()
                    .eventType(EventType.PAYMENT_COMPLETED)
                    .payload(longPayload)
                    .status(EventStatus.PUBLISHED)
                    .build();

            // when
            EventLog savedEventLog = eventLogRepository.save(eventLog);

            // then
            assertThat(savedEventLog).isNotNull();
            assertThat(savedEventLog.getPayload()).isEqualTo(longPayload);
        }
    }

    private static Stream<Arguments> provideEventLogData() {
        return Stream.of(
                Arguments.of(EventType.ORDER_CREATED, "{\"orderId\": 1}", EventStatus.PENDING),
                Arguments.of(EventType.PAYMENT_COMPLETED, "{\"paymentId\": 1}", EventStatus.PUBLISHED),
                Arguments.of(EventType.ORDER_CREATED, "{\"orderId\": 2}", EventStatus.PUBLISHED)
        );
    }
}