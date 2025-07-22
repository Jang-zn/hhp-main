package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.domain.exception.*;

import java.util.Map;

@Schema(description = "쿠폰 관련 요청")
public class CouponRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1")
    @NotNull(message = UserException.Messages.INVALID_USER_ID)
    @Positive(message = UserException.Messages.INVALID_USER_ID_POSITIVE)
    private Long userId;
    
    @Schema(description = "쿠폰 ID", example = "1")
    @Positive(message = CouponException.Messages.INVALID_COUPON_ID_POSITIVE)
    private Long couponId;
    
    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    @Positive(message = CommonException.Messages.INVALID_LIMIT)
    @Max(value = 100, message = CommonException.Messages.LIMIT_EXCEEDED)
    private int limit = 10;
    
    @Schema(description = "페이지 오프셋", example = "0", defaultValue = "0")
    @PositiveOrZero(message = CommonException.Messages.INVALID_OFFSET)
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
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "userId", new SchemaInfo("사용자 ID", "1"),
                "couponId", new SchemaInfo("쿠폰 ID", "1"),
                "limit", new SchemaInfo("페이지 크기", "10", false),
                "offset", new SchemaInfo("페이지 오프셋", "0", false)
        );
    }
}