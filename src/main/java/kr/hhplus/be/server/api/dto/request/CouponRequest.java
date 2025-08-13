package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.ErrorCode;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;

@Schema(description = "쿠폰 관련 요청")
public class CouponRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1")
    @NotNull
    @Positive
    private Long userId;
    
    @Schema(description = "쿠폰 ID", example = "1")
    @NotNull
    @Positive
    private Long couponId;
    
    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    @Positive
    @Max(value = 100)
    private int limit = 10;
    
    @Schema(description = "페이지 오프셋", example = "0", defaultValue = "0")
    @PositiveOrZero
    private int offset = 0;

    // 기본 생성자
    public CouponRequest() {}

    // 생성자들
    public CouponRequest(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
    }
    
    public CouponRequest(int limit, int offset) {
        this.limit = limit;
        this.offset = offset;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }

    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("userId", "사용자 ID", "1")
                .field("couponId", "쿠폰 ID", "1")
                .field("limit", "페이지 크기", "10", false)
                .field("offset", "페이지 오프셋", "0", false)
                .build();
    }
    
    /**
     * 쿠폰 발급 요청
     */
    @Schema(description = "쿠폰 발급 요청")
    public static class IssueCouponRequest {
        @Schema(description = "사용자 ID", example = "1")
        @NotNull
        @Positive
        private Long userId;
        
        @Schema(description = "쿠폰 ID", example = "1")
        @NotNull
        @Positive
        private Long couponId;
        
        public IssueCouponRequest() {}
        
        public IssueCouponRequest(Long userId, Long couponId) {
            this.userId = userId;
            this.couponId = couponId;
        }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getCouponId() { return couponId; }
        public void setCouponId(Long couponId) { this.couponId = couponId; }
    }
}