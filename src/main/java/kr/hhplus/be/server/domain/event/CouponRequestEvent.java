package kr.hhplus.be.server.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 요청 이벤트
 * 
 * 사용자가 선착순 쿠폰을 요청할 때 발생하는 이벤트입니다.
 * userId를 기반으로 파티셔닝하여 동일 사용자 요청의 순서를 보장합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRequestEvent {
    
    /**
     * 요청 고유 ID
     */
    private String requestId;
    
    /**
     * 사용자 ID
     */
    private Long userId;
    
    /**
     * 쿠폰 ID
     */
    private Long couponId;
    
    /**
     * 요청 시간
     */
    private LocalDateTime requestedAt;
    
    /**
     * 클라이언트 정보 (선택사항)
     */
    private String clientInfo;
    
    /**
     * 요청 출처 (web, mobile-app, api 등)
     */
    private String source;

    /**
     * 팩토리 메서드: 기본 쿠폰 요청 이벤트 생성
     */
    public static CouponRequestEvent create(Long userId, Long couponId) {
        return CouponRequestEvent.builder()
                .requestId(generateRequestId(userId, couponId))
                .userId(userId)
                .couponId(couponId)
                .requestedAt(LocalDateTime.now())
                .source("web")
                .build();
    }

    /**
     * 팩토리 메서드: 상세 정보를 포함한 쿠폰 요청 이벤트 생성
     */
    public static CouponRequestEvent create(Long userId, Long couponId, 
                                          String clientInfo, String source) {
        return CouponRequestEvent.builder()
                .requestId(generateRequestId(userId, couponId))
                .userId(userId)
                .couponId(couponId)
                .requestedAt(LocalDateTime.now())
                .clientInfo(clientInfo)
                .source(source)
                .build();
    }

    /**
     * 요청 ID 생성 (중복 요청 방지용)
     */
    private static String generateRequestId(Long userId, Long couponId) {
        return String.format("COUPON_REQ_%d_%d_%d", 
                           userId, couponId, System.currentTimeMillis());
    }

    /**
     * 파티션 키 생성 (userId 기반 파티셔닝)
     */
    @JsonIgnore
    public String getPartitionKey() {
        return "user:" + userId;
    }

    @Override
    public String toString() {
        return String.format("CouponRequestEvent{requestId='%s', userId=%d, couponId=%d, requestedAt=%s}", 
                           requestId, userId, couponId, requestedAt);
    }
}