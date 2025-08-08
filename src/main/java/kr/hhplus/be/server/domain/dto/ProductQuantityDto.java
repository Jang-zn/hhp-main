package kr.hhplus.be.server.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    
    @NotNull
    @Positive
    private Long productId;
    
    @NotNull
    @Positive
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
    
}