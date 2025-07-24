package kr.hhplus.be.server.unit.entity;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.exception.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 엔티티 단위 테스트")
class ProductTest {

    @Nested
    @DisplayName("재고 예약 테스트")
    class ReserveStockTests {
        
        @Test
        @DisplayName("성공케이스: 정상 재고 예약")
        void reserveStock_Success() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            
            // when
            product.reserveStock(30);
            
            // then
            assertThat(product.getStock()).isEqualTo(100); // 실제 재고는 변하지 않음
            assertThat(product.getReservedStock()).isEqualTo(30); // 예약 재고만 증가
        }
        
        @Test
        @DisplayName("실패케이스: 재고 부족으로 예약 실패")
        void reserveStock_InsufficientStock() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(10)
                    .reservedStock(5) // 이미 5개 예약됨
                    .build();
            
            // when & then
            assertThatThrownBy(() -> product.reserveStock(10)) // 남은 재고는 5개인데 10개 예약
                    .isInstanceOf(ProductException.OutOfStock.class)
                    .hasMessage(ProductException.Messages.OUT_OF_STOCK);
        }
        
        @Test
        @DisplayName("실패케이스: 음수 수량으로 예약")
        void reserveStock_NegativeQuantity() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> product.reserveStock(-5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be positive");
        }
        
        @Test
        @DisplayName("실패케이스: 0 수량으로 예약")
        void reserveStock_ZeroQuantity() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> product.reserveStock(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be positive");
        }
    }

    @Nested
    @DisplayName("예약 확정 테스트")
    class ConfirmReservationTests {
        
        @Test
        @DisplayName("성공케이스: 정상 예약 확정")
        void confirmReservation_Success() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(30)
                    .build();
            
            // when
            product.confirmReservation(20);
            
            // then
            assertThat(product.getStock()).isEqualTo(80); // 실제 재고 차감
            assertThat(product.getReservedStock()).isEqualTo(10); // 예약 재고 감소
        }
        
        @Test
        @DisplayName("실패케이스: 예약된 재고보다 많은 확정")
        void confirmReservation_ExceedsReserved() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(10)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> product.confirmReservation(20)) // 예약은 10개인데 20개 확정
                    .isInstanceOf(ProductException.InvalidReservation.class)
                    .hasMessage("Cannot confirm more than reserved quantity");
        }
    }

    @Nested
    @DisplayName("예약 취소 테스트")
    class CancelReservationTests {
        
        @Test
        @DisplayName("성공케이스: 정상 예약 취소")
        void cancelReservation_Success() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(30)
                    .build();
            
            // when
            product.cancelReservation(20);
            
            // then
            assertThat(product.getStock()).isEqualTo(100); // 실제 재고는 변하지 않음
            assertThat(product.getReservedStock()).isEqualTo(10); // 예약 재고만 감소
        }
        
        @Test
        @DisplayName("실패케이스: 예약된 재고보다 많은 취소")
        void cancelReservation_ExceedsReserved() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(10)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> product.cancelReservation(20)) // 예약은 10개인데 20개 취소
                    .isInstanceOf(ProductException.InvalidReservation.class)
                    .hasMessage("Cannot cancel more than reserved quantity");
        }
    }

    @Nested
    @DisplayName("이용 가능한 재고 확인 테스트")
    class HasAvailableStockTests {
        
        @Test
        @DisplayName("성공케이스: 이용 가능한 재고 있음")
        void hasAvailableStock_Available() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(30) // 실제 이용 가능: 70개
                    .build();
            
            // when & then
            assertThat(product.hasAvailableStock(50)).isTrue();
            assertThat(product.hasAvailableStock(70)).isTrue();
        }
        
        @Test
        @DisplayName("실패케이스: 이용 가능한 재고 부족")
        void hasAvailableStock_NotAvailable() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(30) // 실제 이용 가능: 70개
                    .build();
            
            // when & then
            assertThat(product.hasAvailableStock(80)).isFalse();
            assertThat(product.hasAvailableStock(100)).isFalse();
        }
    }

    @Nested
    @DisplayName("기존 재고 차감 테스트")
    class DecreaseStockTests {
        
        @Test
        @DisplayName("성공케이스: 정상 재고 차감")
        void decreaseStock_Success() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .reservedStock(0)
                    .build();
            
            // when
            product.decreaseStock(30);
            
            // then
            assertThat(product.getStock()).isEqualTo(70);
        }
        
        @Test
        @DisplayName("실패케이스: 재고 부족으로 차감 실패")
        void decreaseStock_InsufficientStock() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(10)
                    .reservedStock(0)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> product.decreaseStock(20))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Product stock exceeded");
        }
    }
}