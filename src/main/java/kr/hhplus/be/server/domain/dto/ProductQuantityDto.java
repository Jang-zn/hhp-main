package kr.hhplus.be.server.domain.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 수량 정보를 타입 안전하게 전달하는 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProductQuantityDto {
    
    private Long productId;
    private Integer quantity;
    
    /**
     * Map<Long, Integer>을 ProductQuantityDto 리스트로 변환
     */
    public static List<ProductQuantityDto> fromMap(Map<Long, Integer> productQuantities) {
        if (productQuantities == null) {
            return List.of();
        }
        
        return productQuantities.entrySet().stream()
                .map(entry -> new ProductQuantityDto(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    
    /**
     * ProductQuantityDto 리스트를 Map<Long, Integer>로 변환 (하위 호환성용)
     */
    public static Map<Long, Integer> toMap(List<ProductQuantityDto> productQuantities) {
        if (productQuantities == null) {
            return Map.of();
        }
        
        return productQuantities.stream()
                .collect(Collectors.toMap(
                    ProductQuantityDto::getProductId,
                    ProductQuantityDto::getQuantity
                ));
    }
    
    /**
     * 검증 메서드
     */
    public void validate() {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다");
        }
    }
}