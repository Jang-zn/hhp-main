package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.ErrorCode;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;
import java.util.List;

@Schema(description = "주문 관련 요청")
public class OrderRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1")
    @NotNull
    @Positive
    private Long userId;
    
    @Schema(description = "상품 ID 목록", example = "[1, 2, 3]")
    private List<@Positive Long> productIds;
    
    @Schema(description = "상품 정보 목록 (ID와 수량)", example = "[{\"productId\": 1, \"quantity\": 2}, {\"productId\": 2, \"quantity\": 1}]")
    @Valid
    private List<ProductQuantity> products;
    
    @Schema(description = "쿠폰 ID 목록", example = "[1, 2]")
    private List<@Positive Long> couponIds;
    
    @Schema(description = "쿠폰 ID (결제 시 사용)", example = "1")
    @Positive
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

    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("userId", "사용자 ID", "1")
                .field("productIds", "상품 ID 목록", "[1, 2, 3]", false)
                .field("products", "상품 정보 목록 (ID와 수량)", "[{\"productId\": 1, \"quantity\": 2}]", false)
                .field("couponIds", "쿠폰 ID 목록", "[1, 2]", false)
                .field("couponId", "쿠폰 ID (결제 시 사용)", "1", false)
                .build();
    }
    
    
    @Schema(description = "상품 정보 (ID와 수량)")
    public static class ProductQuantity {
        @Schema(description = "상품 ID", example = "1")
        @NotNull
        @Positive
        private Long productId;
        
        @Schema(description = "수량", example = "2")
        @NotNull
        @Positive
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
    
    /**
     * 주문 생성 요청
     */
    @Schema(description = "주문 생성 요청")
    public static class CreateOrderRequest {
        @Schema(description = "사용자 ID", example = "1")
        @NotNull
        @Positive
        private Long userId;
        
        @Schema(description = "상품 및 수량 정보")
        @NotNull
        @Valid
        private List<ProductQuantityRequest> productQuantities;
        
        public CreateOrderRequest() {}
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public List<ProductQuantityRequest> getProductQuantities() { return productQuantities; }
        public void setProductQuantities(List<ProductQuantityRequest> productQuantities) { this.productQuantities = productQuantities; }
    }
    
    /**
     * 상품 수량 요청
     */
    @Schema(description = "상품 수량 정보")
    public static class ProductQuantityRequest {
        @Schema(description = "상품 ID", example = "1")
        @NotNull
        @Positive
        private Long productId;
        
        @Schema(description = "수량", example = "2")
        @NotNull
        @Positive
        private Integer quantity;
        
        public ProductQuantityRequest() {}
        
        public ProductQuantityRequest(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}