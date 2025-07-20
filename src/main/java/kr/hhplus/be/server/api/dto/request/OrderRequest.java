package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

import java.util.List;

@Schema(description = "주문 관련 요청")
public class OrderRequest {
    
    @Schema(description = "사용자 ID", example = "1")
    @NotNull(message = "사용자 ID는 필수입니다")
    @Positive(message = "사용자 ID는 양수여야 합니다")
    private Long userId;
    
    @Schema(description = "상품 ID 목록", example = "[1, 2, 3]")
    @NotEmpty(message = "상품 목록은 필수입니다")
    private List<Long> productIds;
    
    @Schema(description = "상품 정보 목록 (ID와 수량)", example = "[{\"productId\": 1, \"quantity\": 2}, {\"productId\": 2, \"quantity\": 1}]")
    @Valid
    private List<ProductQuantity> products;
    
    @Schema(description = "쿠폰 ID 목록", example = "[1, 2]")
    private List<Long> couponIds;
    
    @Schema(description = "쿠폰 ID (결제 시 사용)", example = "1")
    @Positive(message = "쿠폰 ID는 양수여야 합니다")
    private Long couponId;

    // 기본 생성자
    public OrderRequest() {}

    // 주문 생성용 생성자
    public OrderRequest(Long userId, List<Long> productIds, List<Long> couponIds) {
        this.userId = userId;
        this.productIds = productIds;
        this.couponIds = couponIds;
    }
    
    // 결제용 생성자
    public OrderRequest(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public List<Long> getProductIds() { return productIds; }
    public void setProductIds(List<Long> productIds) { this.productIds = productIds; }
    public List<ProductQuantity> getProducts() { return products; }
    public void setProducts(List<ProductQuantity> products) { this.products = products; }
    public List<Long> getCouponIds() { return couponIds; }
    public void setCouponIds(List<Long> couponIds) { this.couponIds = couponIds; }
    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }
    
    @Schema(description = "상품 정보 (ID와 수량)")
    public static class ProductQuantity {
        @Schema(description = "상품 ID", example = "1")
        @NotNull(message = "상품 ID는 필수입니다")
        @Positive(message = "상품 ID는 양수여야 합니다")
        private Long productId;
        
        @Schema(description = "수량", example = "2")
        @NotNull(message = "수량은 필수입니다")
        @Positive(message = "수량은 양수여야 합니다")
        private Integer quantity;
        
        public ProductQuantity() {}
        
        public ProductQuantity(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}