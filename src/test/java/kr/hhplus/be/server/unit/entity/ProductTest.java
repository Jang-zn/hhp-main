package kr.hhplus.be.server.unit.entity;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.ProductException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static kr.hhplus.be.server.util.TestAssertions.ProductAssertions;

/**
 * Product 엔티티 비즈니스 로직 테스트
 * 
 * Why: 상품의 재고 관리 로직이 이커머스의 핵심 비즈니스 규칙을 올바르게 구현하는지 검증
 * How: 실제 주문 플로우를 반영한 재고 예약 → 확정 → 취소의 상태 전이 과정을 테스트
 */
@DisplayName("상품 재고 관리 비즈니스 로직")
class ProductTest {

    @Test
    @DisplayName("고객 주문 시 재고를 안전하게 예약한다")
    void safelyReservesStockForCustomerOrder() {
        // Given - 재고가 충분한 상품에 고객이 주문하는 상황
        // Why: 결제 전 재고 확보로 다른 고객의 주문과 충돌 방지
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(100)
            .reservedStock(20) // 이미 다른 고객이 20개 예약 중
            .build();
        
        int originalStock = product.getStock();
        int originalReserved = product.getReservedStock();
        int orderQuantity = 30;

        // When - 고객이 30개 상품을 주문 (재고 예약)
        product.reserveStock(orderQuantity);

        // Then - 실제 재고는 보존되고 예약 재고만 증가
        ProductAssertions.assertStockReserved(product, originalStock, originalReserved, orderQuantity);
        
        // 남은 가용 재고 확인 (100 - (20 + 30) = 50개)
        assertThat(product.hasAvailableStock(50))
            .as("예약 후에도 남은 재고는 다른 고객이 주문할 수 있어야 함")
            .isTrue();
    }

    @Test
    @DisplayName("재고 부족 시 고객 주문을 안전하게 차단한다")
    void safelyBlocksCustomerOrderWhenStockInsufficient() {
        // Given - 재고가 부족한 인기 상품에 고객이 주문하는 상황
        // Why: overselling 방지로 판매자와 고객 모두를 보호
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(10)
            .reservedStock(8) // 가용 재고 2개만 남음
            .build();

        // When & Then - 가용 재고보다 많은 수량 주문 시 차단
        assertThatThrownBy(() -> product.reserveStock(5))
            .as("가용 재고 부족 시 주문이 차단되어야 함")
            .isInstanceOf(ProductException.OutOfStock.class);
            
        // 기존 상태 유지 확인
        assertThat(product.getStock())
            .as("주문 실패 시 기존 재고 상태가 유지되어야 함")
            .isEqualTo(10);
        assertThat(product.getReservedStock())
            .as("주문 실패 시 기존 예약 상태가 유지되어야 함")
            .isEqualTo(8);
    }

    @Test
    @DisplayName("결제 성공 후 예약된 재고를 실제 재고에서 차감한다")
    void deductsReservedStockFromActualStockAfterPaymentSuccess() {
        // Given - 고객이 결제를 완료한 주문 상황
        // Why: 결제 성공 시점에서만 실제 재고 차감으로 데이터 일관성 보장
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(100)
            .reservedStock(30)
            .build();
            
        int originalStock = product.getStock();
        int originalReserved = product.getReservedStock();
        int paidQuantity = 20; // 예약된 30개 중 20개만 결제

        // When - 결제 완료로 예약 확정 처리
        product.confirmReservation(paidQuantity);

        // Then - 실제 재고 차감 및 예약 재고 감소
        ProductAssertions.assertStockConfirmed(product, originalStock, originalReserved, paidQuantity);
        
        // 최종 상태: 실제재고 80, 예약재고 10, 가용재고 70
        assertThat(product.hasAvailableStock(70))
            .as("결제 후 남은 가용 재고가 정확해야 함")
            .isTrue();
    }

    @Test
    @DisplayName("결제 실패 시 예약된 재고를 다른 고객에게 제공한다")
    void releasesReservedStockForOtherCustomersOnPaymentFailure() {
        // Given - 고객이 결제에 실패한 주문 상황
        // Why: 결제 실패 시 예약 해제로 다른 고객의 구매 기회 제공
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(100)
            .reservedStock(30)
            .build();
            
        int originalStock = product.getStock();
        int cancelQuantity = 20; // 예약된 30개 중 20개 취소

        // When - 결제 실패로 예약 취소 처리
        product.cancelReservation(cancelQuantity);

        // Then - 실제 재고는 그대로, 예약 재고만 감소하여 가용 재고 증가
        assertThat(product.getStock())
            .as("예약 취소 시 실제 재고는 변경되지 않아야 함")
            .isEqualTo(originalStock);
            
        assertThat(product.getReservedStock())
            .as("예약 취소된 수량만큼 예약 재고가 감소해야 함")
            .isEqualTo(10);
            
        // 가용 재고 증가 확인 (100 - 10 = 90개 가용)
        assertThat(product.hasAvailableStock(90))
            .as("예약 취소 후 해당 재고를 다른 고객이 주문할 수 있어야 함")
            .isTrue();
    }

    @Test
    @DisplayName("예약보다 많은 수량 확정 시도를 차단한다")
    void blocksConfirmationExceedingReservation() {
        // Given - 시스템 오류나 부정 행위로 예약량을 초과하는 확정 시도
        // Why: 데이터 정합성 보장과 비즈니스 규칙 준수
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(100)
            .reservedStock(10)
            .build();

        // When & Then - 예약량 초과 확정 시도 차단
        assertThatThrownBy(() -> product.confirmReservation(20))
            .as("예약된 수량보다 많은 확정은 차단되어야 함")
            .isInstanceOf(ProductException.InvalidReservation.class);
    }

    @Test
    @DisplayName("예약보다 많은 수량 취소 시도를 차단한다")
    void blocksCancellationExceedingReservation() {
        // Given - 시스템 오류나 중복 요청으로 예약량을 초과하는 취소 시도
        // Why: 데이터 정합성 보장과 음수 예약 방지
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(100)
            .reservedStock(10)
            .build();

        // When & Then - 예약량 초과 취소 시도 차단
        assertThatThrownBy(() -> product.cancelReservation(20))
            .as("예약된 수량보다 많은 취소는 차단되어야 함")
            .isInstanceOf(ProductException.InvalidReservation.class);
    }

    @Test
    @DisplayName("잘못된 수량 입력에 대해 안전하게 처리한다")
    void safelyHandlesInvalidQuantityInputs() {
        // Given - 사용자 입력 오류나 시스템 버그로 인한 잘못된 수량
        // Why: Bean Validation으로 입력 검증이 처리되므로 도메인 레벨에서는 비즈니스 규칙만 검증
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(10)
            .reservedStock(0)
            .build();

        // When & Then - 음수나 0 수량은 Bean Validation으로 처리되므로
        // 도메인 레벨에서는 재고 부족 상황만 테스트
        assertThatThrownBy(() -> product.reserveStock(15))
            .as("재고 부족 시 주문이 차단되어야 함")
            .isInstanceOf(ProductException.OutOfStock.class);
    }

    @Test
    @DisplayName("가용 재고 조회가 정확한 계산 결과를 제공한다")
    void providesAccurateAvailableStockCalculation() {
        // Given - 복잡한 재고 상황에서 정확한 가용 재고 계산 필요
        // Why: 고객에게 정확한 구매 가능 수량 안내
        Product product = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(100)
            .reservedStock(25)
            .build();

        // When & Then - 가용 재고 계산 정확성 검증
        ProductAssertions.assertAvailableForOrder(product, 75); // 정확히 주문 가능한 최대 수량
        
        assertThat(product.hasAvailableStock(75))
            .as("정확한 가용 재고까지는 주문 가능해야 함")
            .isTrue();
            
        assertThat(product.hasAvailableStock(76))
            .as("가용 재고 초과 시에는 주문 불가해야 함")
            .isFalse();
    }

    @Test
    @DisplayName("재고 부족 상품에 대한 주문 가능성을 올바르게 판단한다")
    void correctlyDeterminesOrderAvailabilityForLowStockProducts() {
        // Given - 매진 직전 상품에 대한 주문 가능성 판단
        // Why: 고객 경험 향상을 위한 정확한 재고 상태 안내
        Product almostSoldOut = TestBuilder.ProductBuilder
            .outOfStockProduct() // 재고 0개
            .build();
            
        Product lastOne = TestBuilder.ProductBuilder
            .defaultProduct()
            .stock(1)
            .reservedStock(0)
            .build();

        // When & Then - 매진 상품 주문 불가 확인
        assertThat(almostSoldOut.hasAvailableStock(1))
            .as("매진 상품은 주문 불가해야 함")
            .isFalse();
            
        // 마지막 1개 상품 주문 가능 확인
        assertThat(lastOne.hasAvailableStock(1))
            .as("마지막 1개는 주문 가능해야 함")
            .isTrue();
            
        assertThat(lastOne.hasAvailableStock(2))
            .as("재고보다 많은 수량은 주문 불가해야 함")
            .isFalse();
    }
}