package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.event.CouponResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * 선착순 쿠폰 결과 처리 Consumer
 * 
 * coupon-results 토픽에서 쿠폰 처리 결과를 수신하여 
 * 사용자 알림, 통계 수집, 모니터링 등의 후속 처리를 수행합니다.
 * 
 * 파티셔닝: userId 기반으로 파티셔닝하여 동일 사용자의 결과 순서를 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponResultConsumer {


    @KafkaListener(
        topics = "coupon-results",
        groupId = "coupon-result-processor-group",
        containerFactory = "couponResultKafkaListenerContainerFactory"
    )
    public void handleCouponResult(
            CouponResultEvent result,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        String requestId = result.getRequestId();
        Long userId = result.getUserId();
        Long couponId = result.getCouponId();
        
        log.info("쿠폰 결과 수신: partition={}, offset={}, requestId={}, userId={}, couponId={}, success={}, resultCode={}", 
                partition, offset, requestId, userId, couponId, 
                result.isSuccessful(), result.getResultCode().getCode());
        
        try {
            // 처리 성공/실패에 따른 분기 처리
            if (result.isSuccessful()) {
                handleSuccessResult(result);
            } else {
                handleFailureResult(result);
            }
            
            // 성능 통계 수집
            collectPerformanceMetrics(result, partition);
            
            // 수동 커밋
            ack.acknowledge();
            
            log.info("쿠폰 결과 처리 완료: requestId={}, processingTime={}ms", 
                    requestId, result.getProcessingTimeMs());
            
        } catch (Exception e) {
            log.error("쿠폰 결과 처리 실패: requestId={}, userId={}, couponId={}", 
                    requestId, userId, couponId, e);
            
            // 에러 상황에서도 ACK (무한 재시도 방지)
            ack.acknowledge();
        }
    }

    /**
     * 쿠폰 발급 성공 결과 처리
     */
    private void handleSuccessResult(CouponResultEvent result) {
        Long userId = result.getUserId();
        Long couponId = result.getCouponId();
        Long couponHistoryId = result.getCouponHistoryId();
        
        log.info("쿠폰 발급 성공 처리: userId={}, couponId={}, couponHistoryId={}", 
                userId, couponId, couponHistoryId);
        
        // 향후 확장: 사용자 알림, 통계 업데이트, 마케팅 연동 등
        
        log.debug("쿠폰 발급 성공 후속 처리 완료");
    }

    /**
     * 쿠폰 발급 실패 결과 처리
     */
    private void handleFailureResult(CouponResultEvent result) {
        Long userId = result.getUserId();
        Long couponId = result.getCouponId();
        CouponResultEvent.ResultCode resultCode = result.getResultCode();
        String message = result.getMessage();
        
        log.warn("쿠폰 발급 실패 처리: userId={}, couponId={}, resultCode={}, message={}", 
                userId, couponId, resultCode.getCode(), message);
        
        // 실패 유형별 차별화된 처리 (재고부족, 중복발급, 만료 등)
        switch (resultCode) {
            case OUT_OF_STOCK:
            case ALREADY_ISSUED:
            case EXPIRED:
            case NOT_STARTED:
            case SYSTEM_ERROR:
                // 각 유형별 알림 처리는 향후 구현
                break;
            default:
                log.warn("알 수 없는 실패 결과 코드: {}", resultCode);
        }
        
        log.debug("쿠폰 발급 실패 후속 처리 완료");
    }

    /**
     * 성능 메트릭 수집
     */
    private void collectPerformanceMetrics(CouponResultEvent result, int partition) {
        Long processingTime = result.getProcessingTimeMs();
        CouponResultEvent.ResultCode resultCode = result.getResultCode();
        
        log.debug("성능 메트릭 수집: partition={}, processingTime={}ms, resultCode={}", 
                partition, processingTime, resultCode.getCode());
        
        // 성능 임계값 체크
        if (processingTime > 1000) { // 1초 이상
            log.warn("쿠폰 처리 성능 경고: 처리시간이 {}ms로 임계값(1000ms)을 초과했습니다. requestId={}", 
                    processingTime, result.getRequestId());
        }
    }

}