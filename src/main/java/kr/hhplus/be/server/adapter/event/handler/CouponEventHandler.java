package kr.hhplus.be.server.adapter.event.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.domain.dto.EventMessage;
import kr.hhplus.be.server.domain.event.CouponIssuedEvent;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 이벤트 처리자
 * 
 * 쿠폰 관련 이벤트를 받아서 캐시 무효화 및 쿠폰 관련 로직을 처리합니다.
 * - 쿠폰 발급 시 캐시 무효화
 * - 쿠폰 사용 시 관련 캐시 처리
 * - 쿠폰 재고 변경 시 캐시 갱신
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponEventHandler {
    
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;
    
    private static final int COUPON_CACHE_TTL = 3600; // 1시간
    
    /**
     * 처리 가능한 이벤트 타입 확인
     */
    public boolean canHandle(String eventType) {
        return eventType.startsWith("COUPON_");
    }
    
    /**
     * 쿠폰 이벤트 처리
     */
    public void handle(EventMessage eventMessage) {
        log.info("쿠폰 이벤트 처리 시작: eventType={}, eventId={}", 
                eventMessage.getEventType(), eventMessage.getEventId());
        
        try {
            // 이벤트 타입별 처리
            switch (eventMessage.getEventType()) {
                case "COUPON_ISSUED" -> {
                    CouponIssuedEvent couponEvent = parseCouponIssuedEvent(eventMessage);
                    handleCouponIssued(couponEvent);
                }
                case "COUPON_USED" -> {
                    CouponIssuedEvent couponEvent = parseCouponIssuedEvent(eventMessage);
                    handleCouponUsed(couponEvent);
                }
                case "COUPON_STOCK_UPDATED" -> {
                    CouponIssuedEvent couponEvent = parseCouponIssuedEvent(eventMessage);
                    handleCouponStockUpdated(couponEvent);
                }
                default -> {
                    log.debug("처리할 쿠폰 이벤트 로직이 없음: {}", eventMessage.getEventType());
                }
            }
            
            log.info("쿠폰 이벤트 처리 완료: eventType={}", eventMessage.getEventType());
            
        } catch (Exception e) {
            log.error("쿠폰 이벤트 처리 실패: eventId={}", eventMessage.getEventId(), e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }
    
    /**
     * 쿠폰 발급 이벤트 처리
     */
    private void handleCouponIssued(CouponIssuedEvent event) {
        log.debug("쿠폰 발급 이벤트 처리: couponId={}, userId={}", 
                 event.getCouponId(), event.getUserId());
        
        try {
            // 1. 쿠폰 목록 캐시 무효화 (사용자별)
            invalidateUserCouponCaches(event.getUserId());
            
            // 2. 쿠폰 발급 가능 상태 캐시 무효화
            invalidateCouponAvailabilityCaches(event.getCouponId());
            
            // 3. 쿠폰 재고 관련 캐시 무효화
            invalidateCouponStockCaches(event.getCouponId());
            
            log.debug("쿠폰 발급 이벤트 처리 완료: couponId={}, userId={}", 
                     event.getCouponId(), event.getUserId());
            
        } catch (Exception e) {
            log.error("쿠폰 발급 이벤트 처리 실패: couponId={}, userId={}", 
                     event.getCouponId(), event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * 쿠폰 사용 이벤트 처리
     */
    private void handleCouponUsed(CouponIssuedEvent event) {
        log.debug("쿠폰 사용 이벤트 처리: couponId={}, userId={}", 
                 event.getCouponId(), event.getUserId());
        
        try {
            // 1. 사용자 쿠폰 목록 캐시 무효화
            invalidateUserCouponCaches(event.getUserId());
            
            // 2. 쿠폰 상세 정보 캐시 무효화
            invalidateCouponDetailCaches(event.getCouponId());
            
            log.debug("쿠폰 사용 이벤트 처리 완료: couponId={}, userId={}", 
                     event.getCouponId(), event.getUserId());
            
        } catch (Exception e) {
            log.error("쿠폰 사용 이벤트 처리 실패: couponId={}, userId={}", 
                     event.getCouponId(), event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * 쿠폰 재고 업데이트 이벤트 처리
     */
    private void handleCouponStockUpdated(CouponIssuedEvent event) {
        log.debug("쿠폰 재고 업데이트 이벤트 처리: couponId={}", event.getCouponId());
        
        try {
            // 1. 쿠폰 재고 관련 캐시 무효화
            invalidateCouponStockCaches(event.getCouponId());
            
            // 2. 쿠폰 발급 가능 상태 캐시 무효화
            invalidateCouponAvailabilityCaches(event.getCouponId());
            
            log.debug("쿠폰 재고 업데이트 이벤트 처리 완료: couponId={}", event.getCouponId());
            
        } catch (Exception e) {
            log.error("쿠폰 재고 업데이트 이벤트 처리 실패: couponId={}", event.getCouponId(), e);
            throw e;
        }
    }
    
    /**
     * 캐시 무효화 메서드들
     */
    private void invalidateUserCouponCaches(Long userId) {
        try {
            // 기존 KeyGenerator 메서드 활용
            String userCouponPattern = keyGenerator.generateCouponListCachePattern(userId);
            cachePort.evictByPattern(userCouponPattern);
            
            log.debug("사용자 쿠폰 캐시 무효화 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("사용자 쿠폰 캐시 무효화 실패: userId={}", userId, e);
            throw e;
        }
    }
    
    private void invalidateCouponAvailabilityCaches(Long couponId) {
        try {
            // 쿠폰 개별 캐시 무효화
            String couponCacheKey = keyGenerator.generateCouponCacheKey(couponId);
            cachePort.evict(couponCacheKey);
            
            log.debug("쿠폰 캐시 무효화 완료: couponId={}", couponId);
        } catch (Exception e) {
            log.error("쿠폰 캐시 무효화 실패: couponId={}", couponId, e);
            throw e;
        }
    }
    
    private void invalidateCouponStockCaches(Long couponId) {
        try {
            // 쿠폰 카운터 키 무효화 (선착순 쿠폰 재고 관리)
            String couponCounterKey = keyGenerator.generateCouponCounterKey(couponId);
            cachePort.evict(couponCounterKey);
            
            log.debug("쿠폰 재고 캐시 무효화 완료: couponId={}", couponId);
        } catch (Exception e) {
            log.error("쿠폰 재고 캐시 무효화 실패: couponId={}", couponId, e);
            throw e;
        }
    }
    
    private void invalidateCouponDetailCaches(Long couponId) {
        try {
            // 쿠폰 상세 캐시 무효화
            String couponCacheKey = keyGenerator.generateCouponCacheKey(couponId);
            cachePort.evict(couponCacheKey);
            
            log.debug("쿠폰 상세 정보 캐시 무효화 완료: couponId={}", couponId);
        } catch (Exception e) {
            log.error("쿠폰 상세 정보 캐시 무효화 실패: couponId={}", couponId, e);
            throw e;
        }
    }
    
    /**
     * EventMessage에서 CouponIssuedEvent 파싱
     */
    private CouponIssuedEvent parseCouponIssuedEvent(EventMessage eventMessage) {
        try {
            Object payload = eventMessage.getPayload();
            String payloadJson = objectMapper.writeValueAsString(payload);
            return objectMapper.readValue(payloadJson, CouponIssuedEvent.class);
        } catch (Exception e) {
            log.error("CouponIssuedEvent 파싱 실패: eventId={}", eventMessage.getEventId(), e);
            throw new RuntimeException("CouponIssuedEvent 파싱 실패", e);
        }
    }
}