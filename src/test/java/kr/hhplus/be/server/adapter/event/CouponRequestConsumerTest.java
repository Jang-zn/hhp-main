package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.event.CouponResultEvent;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.exception.CouponException;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("선착순 쿠폰 요청 Consumer 테스트")
class CouponRequestConsumerTest {

    @Mock
    private IssueCouponUseCase issueCouponUseCase;

    @Mock
    private EventPort eventPort;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private CouponRequestConsumer couponRequestConsumer;

    private CouponRequestEvent couponRequestEvent;
    private ConsumerRecord<String, CouponRequestEvent> consumerRecord;

    @BeforeEach
    void setUp() {
        couponRequestEvent = CouponRequestEvent.builder()
                .requestId("COUPON_REQ_1_100_1234567890")
                .userId(1L)
                .couponId(100L)
                .requestedAt(LocalDateTime.now())
                .source("web")
                .build();

        consumerRecord = new ConsumerRecord<>("coupon-requests", 0, 0L, "user:1", couponRequestEvent);
    }

    @Test
    @DisplayName("쿠폰 발급 성공 시 성공 이벤트를 발행한다")
    void shouldPublishSuccessEventWhenCouponIssuedSuccessfully() {
        // given
        CouponHistory couponHistory = CouponHistory.builder()
                .id(1L)
                .userId(1L)
                .couponId(100L)
                .build();

        when(issueCouponUseCase.execute(1L, 100L)).thenReturn(couponHistory);

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(issueCouponUseCase).execute(1L, 100L);
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getRequestId().equals("COUPON_REQ_1_100_1234567890") &&
                   resultEvent.getUserId().equals(1L) &&
                   resultEvent.getCouponId().equals(100L) &&
                   resultEvent.isSuccess() &&
                   resultEvent.getResultCode() == CouponResultEvent.ResultCode.SUCCESS &&
                   resultEvent.getCouponHistoryId().equals(1L);
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("쿠폰 재고 부족 시 재고 부족 이벤트를 발행한다")
    void shouldPublishOutOfStockEventWhenCouponOutOfStock() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new CouponException.OutOfStock());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(issueCouponUseCase).execute(1L, 100L);
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getRequestId().equals("COUPON_REQ_1_100_1234567890") &&
                   resultEvent.getUserId().equals(1L) &&
                   resultEvent.getCouponId().equals(100L) &&
                   !resultEvent.isSuccess() &&
                   resultEvent.getResultCode() == CouponResultEvent.ResultCode.OUT_OF_STOCK &&
                   resultEvent.getCouponHistoryId() == null;
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("중복 발급 시 중복 발급 이벤트를 발행한다")
    void shouldPublishAlreadyIssuedEventWhenCouponAlreadyIssued() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new CouponException.AlreadyIssued());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(issueCouponUseCase).execute(1L, 100L);
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getResultCode() == CouponResultEvent.ResultCode.ALREADY_ISSUED &&
                   !resultEvent.isSuccess();
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("만료된 쿠폰 시 만료 이벤트를 발행한다")
    void shouldPublishExpiredEventWhenCouponExpired() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new CouponException.Expired());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getResultCode() == CouponResultEvent.ResultCode.EXPIRED &&
                   !resultEvent.isSuccess();
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("아직 시작 안된 쿠폰 시 시작 전 이벤트를 발행한다")
    void shouldPublishNotStartedEventWhenCouponNotYetStarted() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new CouponException.CouponNotYetStarted());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getResultCode() == CouponResultEvent.ResultCode.NOT_STARTED &&
                   !resultEvent.isSuccess();
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("쿠폰을 찾을 수 없을 시 쿠폰 없음 이벤트를 발행한다")
    void shouldPublishCouponNotFoundEventWhenCouponNotFound() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new CouponException.NotFound());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getResultCode() == CouponResultEvent.ResultCode.COUPON_NOT_FOUND &&
                   !resultEvent.isSuccess();
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("시스템 오류 시 시스템 오류 이벤트를 발행한다")
    void shouldPublishSystemErrorEventWhenSystemError() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new RuntimeException("예상치 못한 오류"));

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getResultCode() == CouponResultEvent.ResultCode.SYSTEM_ERROR &&
                   !resultEvent.isSuccess();
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("이벤트 발행 실패 시에도 ACK를 수행한다")
    void shouldAcknowledgeEvenWhenEventPublishFails() {
        // given
        CouponHistory couponHistory = CouponHistory.builder()
                .id(1L)
                .userId(1L)
                .couponId(100L)
                .build();

        when(issueCouponUseCase.execute(1L, 100L)).thenReturn(couponHistory);
        doThrow(new RuntimeException("이벤트 발행 실패")).when(eventPort).publish(anyString(), any());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("모든 예외 상황에서도 ACK를 수행한다")
    void shouldAlwaysAcknowledgeToPreventInfiniteRetry() {
        // given
        when(issueCouponUseCase.execute(1L, 100L))
                .thenThrow(new CouponException.OutOfStock());
        doThrow(new RuntimeException("이벤트 발행 실패"))
                .when(eventPort).publish(anyString(), any());

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("처리 시간이 측정되어 결과 이벤트에 포함된다")
    void shouldMeasureAndIncludeProcessingTime() throws InterruptedException {
        // given
        CouponHistory couponHistory = CouponHistory.builder()
                .id(1L)
                .userId(1L)
                .couponId(100L)
                .build();

        when(issueCouponUseCase.execute(1L, 100L)).thenAnswer(invocation -> {
            Thread.sleep(10); // 10ms 처리 시간 시뮬레이션
            return couponHistory;
        });

        // when
        couponRequestConsumer.handleCouponRequest(consumerRecord, 0, 0L, acknowledgment);

        // then
        verify(eventPort).publish(eq("coupon-results"), argThat(event -> {
            CouponResultEvent resultEvent = (CouponResultEvent) event;
            return resultEvent.getProcessingTimeMs() != null && 
                   resultEvent.getProcessingTimeMs() >= 10L;
        }));
    }
}