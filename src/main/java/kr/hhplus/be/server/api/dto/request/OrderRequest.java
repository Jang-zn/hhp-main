package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.ErrorCode;

import java.util.List;
import java.util.Map;

@Schema(description = "주문 관련 요청")
public class OrderRequest implements DocumentedDto {
    
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;
    
    @Schema(description = "상품 ID 목록", example = "[1, 2, 3]")
    private List<Long> productIds;
    
    @Schema(description = "상품 정보 목록 (ID와 수량)", example = "[{\"productId\": 1, \"quantity\": 2}, {\"productId\": 2, \"quantity\": 1}]")
    private List<ProductQuantity> products;
    
    @Schema(description = "쿠폰 ID 목록", example = "[1, 2]")
    private List<Long> couponIds;
    
    @Schema(description = "쿠폰 ID (결제 시 사용)", example = "1")
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
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "userId", new SchemaInfo("사용자 ID", "1"),
                "productIds", new SchemaInfo("상품 ID 목록", "[1, 2, 3]", false),
                "products", new SchemaInfo("상품 정보 목록 (ID와 수량)", "[{\"productId\": 1, \"quantity\": 2}]", false),
                "couponIds", new SchemaInfo("쿠폰 ID 목록", "[1, 2]", false),
                "couponId", new SchemaInfo("쿠폰 ID (결제 시 사용)", "1", false)
        );
    }
    
    /**
     * 요청 데이터 검증
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_USER_ID.getMessage());
        }
        if (couponId != null && couponId <= 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }
        
        // products 목록 검증
        if (products != null) {
            for (ProductQuantity product : products) {
                if (product != null) {
                    product.validate();
                }
            }
        } else if (productIds != null) {
            for (Long productId : productIds) {
                if (productId == null || productId <= 0) {
                    throw new IllegalArgumentException(ErrorCode.INVALID_PRODUCT_ID.getMessage());
                }
            }
        }
    }
    
    @Schema(description = "상품 정보 (ID와 수량)")
    public static class ProductQuantity {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;
        
        @Schema(description = "수량", example = "2")
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
        
        /**
         * ProductQuantity 검증
         * @throws IllegalArgumentException 검증 실패 시
         */
        public void validate() {
            if (productId == null) {
                throw new IllegalArgumentException(ErrorCode.INVALID_PRODUCT_ID.getMessage());
            }
            if (productId <= 0) {
                throw new IllegalArgumentException(ErrorCode.INVALID_PRODUCT_ID.getMessage());
            }
            if (quantity == null) {
                throw new IllegalArgumentException(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException(ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
            }
        }
    }
}