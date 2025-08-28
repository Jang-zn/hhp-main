package kr.hhplus.be.server.unit.domain.entity;

import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventLog 도메인 엔티티 테스트
 * 확장 필드, 상태 전이, 재시도 로직 등 전체 기능 검증
 */
class EventLogTest {

    @Test
    @DisplayName("EventLog 빌더로 확장 필드들이 올바르게 설정되는지 확인")
    void shouldCreateEventLogWithExtensionFields() {
        // given
        String correlationId = "TXN-123456789-abc123";
        String externalEndpoint = "redis://streams/order.completed:async";
        int retryCount = 3;
        String errorMessage = "외부 시스템 연결 실패";
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(5);
        String payload = "{\"orderId\": 1, \"userId\": 1}";

        // when
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_DATA_SYNC)
                .payload(payload)
                .status(EventStatus.PENDING)
                .correlationId(correlationId)
                .externalEndpoint(externalEndpoint)
                .retryCount(retryCount)
                .errorMessage(errorMessage)
                .nextRetryAt(nextRetryAt)
                .build();

        // then
        assertThat(eventLog.getCorrelationId()).isEqualTo(correlationId);
        assertThat(eventLog.getExternalEndpoint()).isEqualTo(externalEndpoint);
        assertThat(eventLog.getRetryCount()).isEqualTo(retryCount);
        assertThat(eventLog.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(eventLog.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    @DisplayName("retryCount 기본값이 0으로 설정되는지 확인")
    void shouldHaveDefaultRetryCountZero() {
        // given & when
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_COMPLETED)
                .payload("{\"test\": \"data\"}")
                .status(EventStatus.PENDING)
                .build();

        // then
        assertThat(eventLog.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("상태 전이 메서드가 올바르게 동작하는지 확인")
    void shouldUpdateStatusCorrectly() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.PAYMENT_DATA_SYNC)
                .payload("{\"paymentId\": 1}")
                .status(EventStatus.PENDING)
                .build();

        // when
        eventLog.updateStatus(EventStatus.PUBLISHED);

        // then
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("실패 상태로 변경 시 에러 메시지가 설정되는지 확인")
    void shouldMarkAsFailedWithErrorMessage() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.PRODUCT_STOCK_SYNC)
                .payload("{\"productId\": 1}")
                .status(EventStatus.PENDING)
                .build();

        String errorMessage = "Redis 연결 시간 초과";

        // when
        eventLog.markAsFailed(errorMessage);

        // then
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(eventLog.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("재시도 횟수가 올바르게 증가하는지 확인")
    void shouldIncrementRetryCount() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.BALANCE_TRANSACTION_SYNC)
                .payload("{\"balanceId\": 1}")
                .status(EventStatus.PENDING)
                .retryCount(2)
                .build();

        // when
        eventLog.incrementRetryCount();

        // then
        assertThat(eventLog.getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("외부 엔드포인트 설정이 올바르게 동작하는지 확인")
    void shouldSetExternalEndpoint() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.USER_ACTIVITY_SYNC)
                .payload("{\"userId\": 1}")
                .status(EventStatus.PENDING)
                .build();

        String endpoint = "redis://streams/user.activity:12345";

        // when
        eventLog.setExternalEndpoint(endpoint);

        // then
        assertThat(eventLog.getExternalEndpoint()).isEqualTo(endpoint);
    }

    @Test
    @DisplayName("다음 재시도 시간 설정이 올바르게 동작하는지 확인")
    void shouldSetNextRetryAt() {
        // given
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_DATA_SYNC)
                .payload("{\"orderId\": 1}")
                .status(EventStatus.FAILED)
                .build();

        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(10);

        // when
        eventLog.setNextRetryAt(nextRetryTime);

        // then
        assertThat(eventLog.getNextRetryAt()).isEqualTo(nextRetryTime);
    }

    @Test
    @DisplayName("correlation ID를 통한 이벤트 추적이 가능한지 확인")
    void shouldTrackEventWithCorrelationId() {
        // given
        String correlationId = "TXN-987654321-xyz789";

        // when
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_COMPLETED)
                .payload("{\"orderId\": 100}")
                .status(EventStatus.PENDING)
                .correlationId(correlationId)
                .build();

        // then
        assertThat(eventLog.getCorrelationId()).isEqualTo(correlationId);
        // 추적을 위해 correlation ID는 null이나 empty가 아니어야 함
        assertThat(eventLog.getCorrelationId()).isNotEmpty();
    }

    // ==== 상태 전이 테스트 ====
    
    @Test
    @DisplayName("PENDING -> PUBLISHED 상태 전이가 올바르게 동작하는지 확인")
    void shouldTransitionFromPendingToPublished() {
        // given
        EventLog eventLog = createEventLog(EventStatus.PENDING);
        
        // when
        eventLog.updateStatus(EventStatus.PUBLISHED);
        
        // then
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("PUBLISHED -> COMPLETED 상태 전이가 올바르게 동작하는지 확인")
    void shouldTransitionFromPublishedToCompleted() {
        // given
        EventLog eventLog = createEventLog(EventStatus.PUBLISHED);
        
        // when
        eventLog.updateStatus(EventStatus.COMPLETED);
        
        // then
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    @DisplayName("어떤 상태에서든 FAILED로 전이 가능한지 확인")
    void shouldTransitionToFailedFromAnyState() {
        // given - PENDING 상태에서 실패 테스트
        EventLog pendingEventLog = createEventLog(EventStatus.PENDING);
        String errorMessage = "외부 시스템 연결 실패";
        
        // when
        pendingEventLog.markAsFailed(errorMessage);
        
        // then
        assertThat(pendingEventLog.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(pendingEventLog.getErrorMessage()).isEqualTo(errorMessage);
        
        // given - PUBLISHED 상태에서 실패 테스트
        EventLog publishedEventLog = createEventLog(EventStatus.PUBLISHED);
        
        // when
        publishedEventLog.markAsFailed(errorMessage);
        
        // then
        assertThat(publishedEventLog.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(publishedEventLog.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("실패 후 재시도를 위한 상태 설정이 올바른지 확인")
    void shouldSetupRetryAfterFailure() {
        // given
        EventLog eventLog = createEventLog(EventStatus.PENDING);
        String errorMessage = "일시적 네트워크 오류";
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(5);
        
        // when
        eventLog.markAsFailed(errorMessage);
        eventLog.incrementRetryCount();
        eventLog.setNextRetryAt(nextRetryTime);
        
        // then
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(eventLog.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(eventLog.getRetryCount()).isEqualTo(1);
        assertThat(eventLog.getNextRetryAt()).isEqualTo(nextRetryTime);
    }

    @Test
    @DisplayName("재시도 후 성공하는 시나리오 검증")
    void shouldHandleRetrySuccessScenario() {
        // given - 처음 실패한 이벤트
        EventLog eventLog = createEventLog(EventStatus.FAILED);
        eventLog.incrementRetryCount(); // retry count = 1
        eventLog.setErrorMessage("첫 번째 시도 실패");
        
        // when - 재시도 후 성공
        eventLog.updateStatus(EventStatus.PENDING); // 재시도 시작
        eventLog.updateStatus(EventStatus.PUBLISHED); // 발행 성공
        eventLog.updateStatus(EventStatus.COMPLETED); // 처리 완료
        
        // then
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.COMPLETED);
        assertThat(eventLog.getRetryCount()).isEqualTo(1); // 재시도 횟수는 유지
        assertThat(eventLog.getErrorMessage()).isEqualTo("첫 번째 시도 실패"); // 에러 메시지도 유지 (이력 목적)
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 후 최종 실패 처리 검증")
    void shouldHandleMaxRetriesExceeded() {
        // given
        EventLog eventLog = createEventLog(EventStatus.FAILED);
        int maxRetries = 3;
        
        // when - 최대 재시도 횟수까지 증가
        for (int i = 0; i < maxRetries; i++) {
            eventLog.incrementRetryCount();
        }
        eventLog.markAsFailed("최대 재시도 횟수 초과");
        
        // then
        assertThat(eventLog.getRetryCount()).isEqualTo(maxRetries);
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(eventLog.getErrorMessage()).isEqualTo("최대 재시도 횟수 초과");
    }

    @Test
    @DisplayName("correlation ID가 상태 전이와 무관하게 유지되는지 확인")
    void shouldMaintainCorrelationIdThroughStatusTransitions() {
        // given
        String correlationId = "TXN-persistent-id";
        EventLog eventLog = EventLog.builder()
                .eventType(EventType.ORDER_DATA_SYNC)
                .payload("{\"orderId\": 1}")
                .status(EventStatus.PENDING)
                .correlationId(correlationId)
                .build();
        
        // when - 여러 상태 전이
        eventLog.updateStatus(EventStatus.PUBLISHED);
        eventLog.updateStatus(EventStatus.COMPLETED);
        
        // then
        assertThat(eventLog.getCorrelationId()).isEqualTo(correlationId);
        assertThat(eventLog.getStatus()).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    @DisplayName("이벤트 타입별 상태 전이가 올바르게 동작하는지 확인")
    void shouldHandleStatusTransitionForAllEventTypes() {
        // given - 대표적인 이벤트 타입들 테스트
        EventType[] testEventTypes = {
            EventType.ORDER_COMPLETED, 
            EventType.PAYMENT_COMPLETED, 
            EventType.PRODUCT_STOCK_SYNC
        };
        
        for (EventType eventType : testEventTypes) {
            // given
            EventLog eventLog = EventLog.builder()
                    .eventType(eventType)
                    .payload("{\"test\": \"data\"}")
                    .status(EventStatus.PENDING)
                    .build();
            
            // when
            eventLog.updateStatus(EventStatus.PUBLISHED);
            
            // then
            assertThat(eventLog.getEventType()).isEqualTo(eventType);
            assertThat(eventLog.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        }
    }

    private EventLog createEventLog(EventStatus status) {
        return EventLog.builder()
                .eventType(EventType.ORDER_DATA_SYNC)
                .payload("{\"orderId\": 1}")
                .status(status)
                .correlationId("TXN-test-correlation")
                .build();
    }
}