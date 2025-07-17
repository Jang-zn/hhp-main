package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "주문 생성 요청")
public class CreateOrderRequest {
    
    @Schema(description = "사용자 ID", example = "1", required = true)
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
    
    @Schema(description = "상품 ID 목록", example = "[1, 2, 3]", required = true)
    @NotEmpty(message = "상품 목록은 필수입니다")
    private List<Long> productIds;
    
    @Schema(description = "쿠폰 ID 목록", example = "[1, 2]")
    private List<Long> couponIds;

    // 기본 생성자
    public CreateOrderRequest() {}

    // 생성자
    public CreateOrderRequest(Long userId, List<Long> productIds, List<Long> couponIds) {
        this.userId = userId;
        this.productIds = productIds;
        this.couponIds = couponIds;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public List<Long> getProductIds() { return productIds; }
    public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
    public List<Long> getCouponIds() { return couponIds; }
    public void setCouponIds(List<Long> couponIds) { this.couponIds = couponIds; }
} 