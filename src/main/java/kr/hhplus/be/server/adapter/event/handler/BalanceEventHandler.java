package kr.hhplus.be.server.adapter.event.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.domain.event.BalanceUpdatedEvent;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 잔액 이벤트 처리자
 * 
 * 잔액 관련 이벤트를 받아서 캐시 무효화 및 잔액 관련 로직을 처리합니다.
 * - 잔액 충전/차감 시 캐시 무효화
 * - 잔액 변경 로그 및 모니터링
 * - 향후 알림, 포인트 적립 등의 확장 기능
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceEventHandler {
    
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;
    
    /**
     * 처리 가능한 이벤트 타입 확인
     */
    public boolean canHandle(String eventType) {
        return eventType.startsWith("BALANCE_");
    }
    
    /**
     * 잔액 이벤트 처리
     */
    public void handle(EventMessage eventMessage) {
        log.info("잔액 이벤트 처리 시작: eventType={}, eventId={}", 
                eventMessage.getEventType(), eventMessage.getEventId());
        
        try {
            // 이벤트 타입별 처리
            switch (eventMessage.getEventType()) {
                case "BALANCE_CHARGED" -> {
                    BalanceUpdatedEvent balanceEvent = parseBalanceEvent(eventMessage);
                    handleBalanceCharged(balanceEvent);
                }
                case "BALANCE_DEDUCTED" -> {
                    BalanceUpdatedEvent balanceEvent = parseBalanceEvent(eventMessage);
                    handleBalanceDeducted(balanceEvent);
                }
                default -> {
                    log.debug("처리할 잔액 이벤트 로직이 없음: {}", eventMessage.getEventType());
                }
            }
            
            log.info("잔액 이벤트 처리 완료: eventType={}", eventMessage.getEventType());
            
        } catch (Exception e) {
            log.error("잔액 이벤트 처리 실패: eventId={}", eventMessage.getEventId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * 잔액 충전 이벤트 처리
     */
    private void handleBalanceCharged(BalanceUpdatedEvent event) {
        log.debug("잔액 충전 이벤트 처리: userId={}, amount={}", 
                 event.getUserId(), event.getAmount());
        
        try {
            // 1. 사용자 잔액 캐시 무효화
            invalidateBalanceCache(event.getUserId());
            
            // 2. 충전 관련 추가 처리
            log.info("잔액 충전 완료 - 사후 처리: userId={}, chargedAmount={}, currentBalance={}", 
                    event.getUserId(), event.getAmount(), event.getCurrentBalance());
            
            // TODO: 향후 확장 가능한 기능들
            // - 충전 완료 알림 발송
            // - 포인트 적립 (충전 금액의 일정 비율)
            // - VIP 등급 업데이트 체크
            // - 충전 이력 통계 업데이트
            
            log.debug("잔액 충전 이벤트 처리 완료: userId={}", event.getUserId());
            
        } catch (Exception e) {
            log.error("잔액 충전 이벤트 처리 실패: userId={}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * 잔액 차감 이벤트 처리
     */
    private void handleBalanceDeducted(BalanceUpdatedEvent event) {
        log.debug("잔액 차감 이벤트 처리: userId={}, amount={}, orderId={}", 
                 event.getUserId(), event.getAmount(), event.getOrderId());
        
        try {
            // 1. 사용자 잔액 캐시 무효화
            invalidateBalanceCache(event.getUserId());
            
            // 2. 차감 관련 추가 처리
            log.info("잔액 차감 완료 - 사후 처리: userId={}, deductedAmount={}, currentBalance={}, orderId={}", 
                    event.getUserId(), event.getAmount(), event.getCurrentBalance(), event.getOrderId());
            
            // TODO: 향후 확장 가능한 기능들
            // - 잔액 부족 경고 알림 (임계값 이하시)
            // - 결제 완료 알림 발송
            // - 소비 패턴 분석을 위한 데이터 수집
            // - 잔액 부족 시 충전 권유 로직
            
            log.debug("잔액 차감 이벤트 처리 완료: userId={}", event.getUserId());
            
        } catch (Exception e) {
            log.error("잔액 차감 이벤트 처리 실패: userId={}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * 잔액 캐시 무효화
     */
    private void invalidateBalanceCache(Long userId) {
        try {
            // 사용자 잔액 캐시 무효화
            String balanceCacheKey = keyGenerator.generateBalanceCacheKey(userId);
            cachePort.evict(balanceCacheKey);
            
            log.debug("잔액 캐시 무효화 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("잔액 캐시 무효화 실패: userId={}", userId, e);
            throw e;
        }
    }
    
    /**
     * EventMessage에서 BalanceUpdatedEvent 파싱
     */
    private BalanceUpdatedEvent parseBalanceEvent(EventMessage eventMessage) {
        try {
            Object payload = eventMessage.getPayload();
            return objectMapper.convertValue(payload, BalanceUpdatedEvent.class);
        } catch (Exception e) {
            log.error("BalanceUpdatedEvent 파싱 실패: eventId={}", eventMessage.getEventId(), e);
            throw new RuntimeException("BalanceUpdatedEvent 파싱 실패", e);
        }
    }
}