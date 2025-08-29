package kr.hhplus.be.server.unit.domain.repository;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.unit.repository.RepositoryTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventLog Repository 확장 필드 저장/조회 테스트
 * STEP 15 필드들의 영속성 검증
 */
@ActiveProfiles("unit-repository")
class EventLogRepositoryTest extends RepositoryTestBase {

    @Autowired
    private EventLogRepositoryPort eventLogRepository;

    @Test
    @DisplayName("확장 필드들이 올바르게 저장되고 조회되는지 확인")
    void shouldSaveAndRetrieveExtensionFields() {
        // given
        String correlationId = "TXN-123456789-test";
        String externalEndpoint = "redis://streams/test:async";
        int retryCount = 2;
        String errorMessage = "테스트 에러 메시지";
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(5);

        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_DATA_SYNC)
                .payload("{\"test\": \"payload\"}")
                .status(EventStatus.FAILED)
                .correlationId(correlationId)
                .externalEndpoint(externalEndpoint)
                .retryCount(retryCount)
                .errorMessage(errorMessage)
                .nextRetryAt(nextRetryAt)
                .build();

        // when
        EventLog saved = eventLogRepository.save(eventLog);

        // then
        assertThat(saved.getId()).isNotNull();
        
        // 저장된 데이터 검증
        Optional<EventLog> retrieved = eventLogRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        
        EventLog found = retrieved.get();
        assertThat(found.getCorrelationId()).isEqualTo(correlationId);
        assertThat(found.getExternalEndpoint()).isEqualTo(externalEndpoint);
        assertThat(found.getRetryCount()).isEqualTo(retryCount);
        assertThat(found.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(found.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    @DisplayName("correlation ID로 이벤트 로그를 조회할 수 있는지 확인")
    void shouldFindByCorrelationId() {
        // given
        String correlationId = "TXN-unique-correlation-id";
        
        EventLog eventLog1 = EventLog.builder()
                .eventType(EventType.ORDER_COMPLETED)
                .payload("{\"orderId\": 1}")
                .status(EventStatus.PUBLISHED)
                .correlationId(correlationId)
                .build();

        EventLog eventLog2 = EventLog.builder()
                .eventType(EventType.PAYMENT_COMPLETED)
                .payload("{\"paymentId\": 1}")
                .status(EventStatus.PUBLISHED)
                .correlationId("TXN-different-id")
                .build();

        eventLogRepository.save(eventLog1);
        eventLogRepository.save(eventLog2);

        // when
        List<EventLog> results = eventLogRepository.findByCorrelationId(correlationId);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCorrelationId()).isEqualTo(correlationId);
        assertThat(results.get(0).getEventType()).isEqualTo(EventType.ORDER_COMPLETED);
    }

    @Test
    @DisplayName("상태별 이벤트 로그를 조회할 수 있는지 확인")
    void shouldFindByStatus() {
        // given
        EventLog pendingLog = EventLog.builder()
                .eventType(EventType.PRODUCT_STOCK_SYNC)
                .payload("{\"productId\": 1}")
                .status(EventStatus.PENDING)
                .build();

        EventLog failedLog = EventLog.builder()
                .eventType(EventType.BALANCE_TRANSACTION_SYNC)
                .payload("{\"balanceId\": 1}")
                .status(EventStatus.FAILED)
                .build();

        eventLogRepository.save(pendingLog);
        eventLogRepository.save(failedLog);

        // when
        List<EventLog> failedResults = eventLogRepository.findByStatus(EventStatus.FAILED);

        // then
        assertThat(failedResults).hasSize(1);
        assertThat(failedResults.get(0).getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(failedResults.get(0).getEventType()).isEqualTo(EventType.BALANCE_TRANSACTION_SYNC);
    }

}