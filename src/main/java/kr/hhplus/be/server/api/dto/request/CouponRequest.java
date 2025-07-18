package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "쿠폰 관련 요청")
public class CouponRequest {
    
    @Schema(description = "사용자 ID", example = "1")
    @NotNull(message = "사용자 ID는 필수입니다")
    @Positive(message = "사용자 ID는 양수여야 합니다")
    private Long userId;
    
    @Schema(description = "쿠폰 ID", example = "1")
    @Positive(message = "쿠폰 ID는 양수여야 합니다")
    private Long couponId;
    
    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    @Positive(message = "limit은 양수여야 합니다")
    @Max(value = 100, message = "limit은 100 이하여야 합니다")
    private int limit = 10;
    
    @Schema(description = "페이지 오프셋", example = "0", defaultValue = "0")
    @PositiveOrZero(message = "offset은 0 이상이어야 합니다")
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
}