package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.event.CouponResultEvent;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.exception.CouponException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 요청 처리 Consumer
 * 
 * coupon-requests 토픽에서 쿠폰 요청을 수신하여 처리하고,
 * 결과를 coupon-results 토픽으로 발행합니다.
 * 
 * 파티셔닝: userId 기반으로 파티셔닝하여 동일 사용자의 요청 순서를 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRequestConsumer {

    private final IssueCouponUseCase issueCouponUseCase;
    private final EventPort eventPort;

    @KafkaListener(
        topics = "coupon-requests",
        groupId = "coupon-processor-group",
        containerFactory = "couponKafkaListenerContainerFactory"
    )
    public void handleCouponRequest(
            ConsumerRecord<String, CouponRequestEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        CouponRequestEvent request = record.value();
        String requestId = request.getRequestId();
        Long userId = request.getUserId();
        Long couponId = request.getCouponId();
        long startTime = System.currentTimeMillis();
        
        log.info("선착순 쿠폰 요청 수신: partition={}, offset={}, requestId={}, userId={}, couponId={}", 
                partition, offset, requestId, userId, couponId);
        
        try {
            // 선착순 쿠폰 발급 처리
            CouponHistory couponHistory = issueCouponUseCase.execute(userId, couponId);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 성공 결과 이벤트 발행
            CouponResultEvent resultEvent = CouponResultEvent.success(
                requestId, userId, couponId, 
                couponHistory.getId(), processingTime
            );
            
            eventPort.publish("coupon-results", resultEvent);
            
            // 수동 커밋
            ack.acknowledge();
            
            log.info("선착순 쿠폰 발급 성공: requestId={}, userId={}, couponHistoryId={}, processingTime={}ms", 
                    requestId, userId, couponHistory.getId(), processingTime);
            
        } catch (CouponException.OutOfStock e) {
            handleCouponError(requestId, userId, couponId, 
                            CouponResultEvent.ResultCode.OUT_OF_STOCK, 
                            startTime, ack, e);
            
        } catch (CouponException.AlreadyIssued e) {
            handleCouponError(requestId, userId, couponId, 
                            CouponResultEvent.ResultCode.ALREADY_ISSUED, 
                            startTime, ack, e);
            
        } catch (CouponException.Expired e) {
            handleCouponError(requestId, userId, couponId, 
                            CouponResultEvent.ResultCode.EXPIRED, 
                            startTime, ack, e);
            
        } catch (CouponException.CouponNotYetStarted e) {
            handleCouponError(requestId, userId, couponId, 
                            CouponResultEvent.ResultCode.NOT_STARTED, 
                            startTime, ack, e);
            
        } catch (CouponException.NotFound e) {
            handleCouponError(requestId, userId, couponId, 
                            CouponResultEvent.ResultCode.COUPON_NOT_FOUND, 
                            startTime, ack, e);
            
        } catch (Exception e) {
            handleCouponError(requestId, userId, couponId, 
                            CouponResultEvent.ResultCode.SYSTEM_ERROR, 
                            startTime, ack, e);
        }
    }

    /**
     * 쿠폰 처리 에러 핸들링
     */
    private void handleCouponError(String requestId, Long userId, Long couponId, 
                                 CouponResultEvent.ResultCode resultCode,
                                 long startTime, Acknowledgment ack, Exception e) {
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        log.warn("선착순 쿠폰 발급 실패: requestId={}, userId={}, couponId={}, resultCode={}, processingTime={}ms", 
                requestId, userId, couponId, resultCode.getCode(), processingTime, e);
        
        try {
            // 실패 결과 이벤트 발행
            CouponResultEvent resultEvent = CouponResultEvent.failure(
                requestId, userId, couponId, resultCode, processingTime
            );
            
            eventPort.publish("coupon-results", resultEvent);
            
            // 실패 상황에서도 ACK (DLQ로 전송되지 않도록)
            ack.acknowledge();
            
        } catch (Exception publishException) {
            log.error("쿠폰 결과 이벤트 발행 실패: requestId={}, resultCode={}", 
                    requestId, resultCode.getCode(), publishException);
            
            // 이벤트 발행 실패 시에도 ACK (무한 재시도 방지)
            ack.acknowledge();
        }
    }

}