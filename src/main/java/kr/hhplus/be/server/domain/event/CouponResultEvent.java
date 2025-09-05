package kr.hhplus.be.server.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 처리 결과 이벤트
 * 
 * 쿠폰 요청 처리 결과를 사용자에게 알리기 위한 이벤트입니다.
 * 성공/실패 여부와 상세 사유를 포함합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponResultEvent {
    
    /**
     * 원본 요청 ID (CouponRequestEvent의 requestId와 매칭)
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
     * 처리 성공 여부
     */
    private boolean success;
    
    /**
     * 결과 코드
     */
    private ResultCode resultCode;
    
    /**
     * 결과 메시지
     */
    private String message;
    
    /**
     * 발급된 쿠폰 히스토리 ID (성공 시)
     */
    private Long couponHistoryId;
    
    /**
     * 처리 완료 시간
     */
    private LocalDateTime processedAt;
    
    /**
     * 처리 소요 시간 (milliseconds)
     */
    private Long processingTimeMs;

    /**
     * 결과 코드 열거형
     */
    public enum ResultCode {
        SUCCESS("SUCCESS", "쿠폰 발급 성공"),
        OUT_OF_STOCK("OUT_OF_STOCK", "쿠폰 재고 소진"),
        ALREADY_ISSUED("ALREADY_ISSUED", "이미 발급받은 쿠폰"),
        EXPIRED("EXPIRED", "쿠폰 발급 기간 만료"),
        NOT_STARTED("NOT_STARTED", "쿠폰 발급 시작 전"),
        USER_NOT_FOUND("USER_NOT_FOUND", "존재하지 않는 사용자"),
        COUPON_NOT_FOUND("COUPON_NOT_FOUND", "존재하지 않는 쿠폰"),
        SYSTEM_ERROR("SYSTEM_ERROR", "시스템 오류");

        private final String code;
        private final String message;

        ResultCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 팩토리 메서드: 성공 결과 이벤트 생성
     */
    public static CouponResultEvent success(String requestId, Long userId, Long couponId, 
                                          Long couponHistoryId, Long processingTimeMs) {
        return CouponResultEvent.builder()
                .requestId(requestId)
                .userId(userId)
                .couponId(couponId)
                .success(true)
                .resultCode(ResultCode.SUCCESS)
                .message(ResultCode.SUCCESS.getMessage())
                .couponHistoryId(couponHistoryId)
                .processedAt(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * 팩토리 메서드: 실패 결과 이벤트 생성
     */
    public static CouponResultEvent failure(String requestId, Long userId, Long couponId, 
                                          ResultCode resultCode, Long processingTimeMs) {
        return CouponResultEvent.builder()
                .requestId(requestId)
                .userId(userId)
                .couponId(couponId)
                .success(false)
                .resultCode(resultCode)
                .message(resultCode.getMessage())
                .couponHistoryId(null)
                .processedAt(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * 팩토리 메서드: 재고 부족 결과 이벤트 생성
     */
    public static CouponResultEvent outOfStock(String requestId, Long userId, Long couponId, 
                                             Long processingTimeMs) {
        return failure(requestId, userId, couponId, ResultCode.OUT_OF_STOCK, processingTimeMs);
    }

    /**
     * 팩토리 메서드: 중복 발급 결과 이벤트 생성
     */
    public static CouponResultEvent alreadyIssued(String requestId, Long userId, Long couponId, 
                                                Long processingTimeMs) {
        return failure(requestId, userId, couponId, ResultCode.ALREADY_ISSUED, processingTimeMs);
    }

    /**
     * 팩토리 메서드: 시스템 오류 결과 이벤트 생성
     */
    public static CouponResultEvent systemError(String requestId, Long userId, Long couponId, 
                                              Long processingTimeMs) {
        return failure(requestId, userId, couponId, ResultCode.SYSTEM_ERROR, processingTimeMs);
    }

    /**
     * 파티션 키 생성 (userId 기반 파티셔닝)
     */
    public String getPartitionKey() {
        return "user:" + userId;
    }

    /**
     * 성공률 체크 (모니터링용)
     */
    public boolean isSuccessful() {
        return success && resultCode == ResultCode.SUCCESS;
    }

    @Override
    public String toString() {
        return String.format("CouponResultEvent{requestId='%s', userId=%d, couponId=%d, success=%s, resultCode=%s, processingTimeMs=%d}", 
                           requestId, userId, couponId, success, resultCode.getCode(), processingTimeMs);
    }
}