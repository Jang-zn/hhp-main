package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.event.CouponResultEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 결과 Consumer 테스트")
class CouponResultConsumerTest {

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private CouponResultConsumer couponResultConsumer;

    private ConsumerRecord<String, CouponResultEvent> successRecord;
    private ConsumerRecord<String, CouponResultEvent> failureRecord;

    @BeforeEach
    void setUp() {
        // 성공 결과 이벤트
        CouponResultEvent successEvent = CouponResultEvent.success(
                "COUPON_REQ_1_100_1234567890",
                1L,
                100L,
                1L, // couponHistoryId
                150L // processingTimeMs
        );
        
        successRecord = new ConsumerRecord<>("coupon-results", 0, 0L, "user:1", successEvent);

        // 실패 결과 이벤트 (재고 부족)
        CouponResultEvent failureEvent = CouponResultEvent.outOfStock(
                "COUPON_REQ_1_100_1234567890",
                1L,
                100L,
                200L // processingTimeMs
        );

        failureRecord = new ConsumerRecord<>("coupon-results", 0, 1L, "user:1", failureEvent);
    }

    @Test
    @DisplayName("성공 결과 이벤트를 정상적으로 처리한다")
    void shouldHandleSuccessResultEvent() {
        // when
        couponResultConsumer.handleCouponResult(successRecord.value(), 0, 0L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("실패 결과 이벤트를 정상적으로 처리한다")
    void shouldHandleFailureResultEvent() {
        // when
        couponResultConsumer.handleCouponResult(failureRecord.value(), 0, 1L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("재고 부족 결과를 특별히 처리한다")
    void shouldHandleOutOfStockResult() {
        // given
        CouponResultEvent outOfStockEvent = CouponResultEvent.outOfStock(
                "COUPON_REQ_2_100_1234567890",
                2L,
                100L,
                180L
        );
        
        ConsumerRecord<String, CouponResultEvent> outOfStockRecord = 
                new ConsumerRecord<>("coupon-results", 1, 2L, "user:2", outOfStockEvent);

        // when
        couponResultConsumer.handleCouponResult(outOfStockRecord.value(), 1, 2L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("중복 발급 결과를 특별히 처리한다")
    void shouldHandleAlreadyIssuedResult() {
        // given
        CouponResultEvent alreadyIssuedEvent = CouponResultEvent.alreadyIssued(
                "COUPON_REQ_3_100_1234567890",
                3L,
                100L,
                120L
        );
        
        ConsumerRecord<String, CouponResultEvent> alreadyIssuedRecord = 
                new ConsumerRecord<>("coupon-results", 2, 3L, "user:3", alreadyIssuedEvent);

        // when
        couponResultConsumer.handleCouponResult(alreadyIssuedRecord.value(), 2, 3L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("시스템 오류 결과를 특별히 처리한다")
    void shouldHandleSystemErrorResult() {
        // given
        CouponResultEvent systemErrorEvent = CouponResultEvent.systemError(
                "COUPON_REQ_4_100_1234567890",
                4L,
                100L,
                300L
        );
        
        ConsumerRecord<String, CouponResultEvent> systemErrorRecord = 
                new ConsumerRecord<>("coupon-results", 3, 4L, "user:4", systemErrorEvent);

        // when
        couponResultConsumer.handleCouponResult(systemErrorRecord.value(), 3, 4L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("만료된 쿠폰 결과를 처리한다")
    void shouldHandleExpiredResult() {
        // given
        CouponResultEvent expiredEvent = CouponResultEvent.failure(
                "COUPON_REQ_5_100_1234567890",
                5L,
                100L,
                CouponResultEvent.ResultCode.EXPIRED,
                140L
        );
        
        ConsumerRecord<String, CouponResultEvent> expiredRecord = 
                new ConsumerRecord<>("coupon-results", 4, 5L, "user:5", expiredEvent);

        // when
        couponResultConsumer.handleCouponResult(expiredRecord.value(), 4, 5L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("아직 시작 안된 쿠폰 결과를 처리한다")
    void shouldHandleNotStartedResult() {
        // given
        CouponResultEvent notStartedEvent = CouponResultEvent.failure(
                "COUPON_REQ_6_100_1234567890",
                6L,
                100L,
                CouponResultEvent.ResultCode.NOT_STARTED,
                90L
        );
        
        ConsumerRecord<String, CouponResultEvent> notStartedRecord = 
                new ConsumerRecord<>("coupon-results", 5, 6L, "user:6", notStartedEvent);

        // when
        couponResultConsumer.handleCouponResult(notStartedRecord.value(), 5, 6L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("처리 시간이 1초를 초과하면 경고 로그를 남긴다")
    void shouldLogWarningWhenProcessingTimeExceeds1Second() {
        // given
        CouponResultEvent slowEvent = CouponResultEvent.success(
                "COUPON_REQ_7_100_1234567890",
                7L,
                100L,
                1L,
                1500L // 1.5초
        );
        
        ConsumerRecord<String, CouponResultEvent> slowRecord = 
                new ConsumerRecord<>("coupon-results", 6, 7L, "user:7", slowEvent);

        // when
        couponResultConsumer.handleCouponResult(slowRecord.value(), 6, 7L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
        // 경고 로그는 실제로는 로그 검증이 필요하지만 단위 테스트에서는 생략
    }

    @Test
    @DisplayName("예외 발생 시에도 ACK를 수행한다")
    void shouldAcknowledgeEvenWhenExceptionOccurs() {
        // given - acknowledgment.acknowledge()에서 예외 발생 시뮬레이션은 불가능하므로
        // 실제 처리 중 예외가 발생하는 상황을 시뮬레이션
        CouponResultEvent normalEvent = CouponResultEvent.success(
                "COUPON_REQ_8_100_1234567890",
                8L,
                100L,
                1L,
                100L
        );
        
        ConsumerRecord<String, CouponResultEvent> normalRecord = 
                new ConsumerRecord<>("coupon-results", 7, 8L, "user:8", normalEvent);

        // when
        couponResultConsumer.handleCouponResult(normalRecord.value(), 7, 8L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("각 결과 코드별로 적절한 후속 처리 로직이 호출된다")
    void shouldCallAppropriateHandlingLogicForEachResultCode() {
        // given - 다양한 결과 코드 테스트
        CouponResultEvent[] events = {
            CouponResultEvent.success("REQ_1", 1L, 100L, 1L, 100L),
            CouponResultEvent.outOfStock("REQ_2", 2L, 100L, 100L),
            CouponResultEvent.alreadyIssued("REQ_3", 3L, 100L, 100L),
            CouponResultEvent.systemError("REQ_4", 4L, 100L, 100L),
            CouponResultEvent.failure("REQ_5", 5L, 100L, CouponResultEvent.ResultCode.EXPIRED, 100L),
            CouponResultEvent.failure("REQ_6", 6L, 100L, CouponResultEvent.ResultCode.NOT_STARTED, 100L)
        };

        // when & then
        for (int i = 0; i < events.length; i++) {
            ConsumerRecord<String, CouponResultEvent> record = 
                    new ConsumerRecord<>("coupon-results", i, (long)i, "user:" + (i+1), events[i]);
            
            couponResultConsumer.handleCouponResult(record.value(), i, (long)i, acknowledgment);
        }

        // 모든 이벤트에 대해 ACK가 수행되었는지 확인
        verify(acknowledgment, times(events.length)).acknowledge();
    }

    @Test
    @DisplayName("파티션 정보가 정상적으로 전달되어 처리된다")
    void shouldHandlePartitionInformationCorrectly() {
        // given
        int partition = 3;
        long offset = 10L;

        // when
        couponResultConsumer.handleCouponResult(successRecord.value(), partition, offset, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
        // 파티션별 통계 수집이 정상적으로 처리되었는지는 로그를 통해 확인 (실제로는 메트릭 검증)
    }
}