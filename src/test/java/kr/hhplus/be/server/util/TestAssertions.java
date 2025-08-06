package kr.hhplus.be.server.util;

import kr.hhplus.be.server.domain.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * 테스트용 비즈니스 도메인 검증 헬퍼 클래스
 * 
 * Why: 비즈니스 도메인에 특화된 고수준 검증 메서드 제공
 * How: 구현 세부사항보다는 비즈니스 의미에 집중한 검증
 * 
 * Design Principle: 
 * - 구현 변경에 덜 취약하도록 고수준 추상화
 * - 비즈니스 의미가 명확한 메서드명 사용
 * - 너무 세부적인 검증보다는 핵심 비즈니스 규칙에 집중
 */
public class TestAssertions {

    /**
     * Product 비즈니스 검증
     * 구현보다는 비즈니스 의미에 집중
     */
    public static class ProductAssertions {
        
        /**
         * 상품이 주문 가능한 상태인지 검증
         * 구현 세부사항보다는 비즈니스 목적에 집중
         */
        public static void assertAvailableForOrder(Product product, int requestQuantity) {
            assertThat(product.hasAvailableStock(requestQuantity))
                .as("상품이 요청한 수량만큼 주문 가능해야 함")
                .isTrue();
        }
        
        /**
         * 재고 예약 후 비즈니스 불변식 확인
         * 세부 구현보다는 핵심 규칙에 집중
         */
        public static void assertReservationSuccess(Product product) {
            assertThat(product.getStock()).as("재고 예약 후에도 총 재고는 양수여야 함").isGreaterThanOrEqualTo(0);
            assertThat(product.getReservedStock()).as("예약 재고는 음수가 될 수 없음").isGreaterThanOrEqualTo(0);
            assertThat(product.getStock()).as("예약 재고는 총 재고를 초과할 수 없음").isGreaterThanOrEqualTo(product.getReservedStock());
        }
        
        /**
         * 재고 예약 후 변화량 검증 (기존 테스트 호환성)
         */
        public static void assertStockReserved(Product product, int originalStock, int originalReserved, int orderQuantity) {
            assertThat(product.getStock())
                .as("전체 재고는 변하지 않아야 함")
                .isEqualTo(originalStock);
            assertThat(product.getReservedStock())
                .as("예약 재고가 주문 수량만큼 증가해야 함")
                .isEqualTo(originalReserved + orderQuantity);
        }
        
        /**
         * 재고 확정 후 변화량 검증 (기존 테스트 호환성)
         */
        public static void assertStockConfirmed(Product product, int originalStock, int originalReserved, int paidQuantity) {
            assertThat(product.getStock())
                .as("전체 재고가 확정 수량만큼 감소해야 함")
                .isEqualTo(originalStock - paidQuantity);
            assertThat(product.getReservedStock())
                .as("예약 재고가 확정 수량만큼 감소해야 함")
                .isEqualTo(originalReserved - paidQuantity);
        }
    }

    /**
     * Balance 비즈니스 검증
     */
    public static class BalanceAssertions {
        
        /**
         * 잔액 연산 후 비즈니스 불변식 확인
         */
        public static void assertBalanceIntegrity(Balance balance) {
            assertThat(balance).as("잔액 객체는 null이 될 수 없음").isNotNull();
            assertThat(balance.getAmount()).as("잔액은 음수가 될 수 없음").isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(balance.getUserId()).as("사용자 ID는 유효해야 함").isPositive();
        }
        
        /**
         * 결제 가능 여부만 확인 (구체적인 금액 비교는 테스트에서 직접)
         */
        public static void assertSufficientForPayment(Balance balance, BigDecimal paymentAmount) {
            assertThat(balance.getAmount())
                .as("잔액이 결제 금액보다 충분해야 함")
                .isGreaterThanOrEqualTo(paymentAmount);
        }
        
        /**
         * 잔액 저장 검증 (기존 테스트 호환성)
         */
        public static void assertSavedCorrectly(Balance saved, Balance expected) {
            assertBalanceIntegrity(saved);
            assertThat(saved.getUserId()).as("사용자 ID가 일치해야 함").isEqualTo(expected.getUserId());
            assertThat(saved.getAmount()).as("잔액이 일치해야 함").isEqualByComparingTo(expected.getAmount());
        }
        
        /**
         * 잔액 충전 후 검증 (기존 테스트 호환성)
         */
        public static void assertCharged(Balance result, BigDecimal originalAmount, BigDecimal chargeAmount) {
            assertBalanceIntegrity(result);
            assertThat(result.getAmount())
                .as("충전 후 잔액이 정확해야 함")
                .isEqualByComparingTo(originalAmount.add(chargeAmount));
        }
        
        /**
         * 잔액 차감 후 검증 (기존 테스트 호환성)
         */
        public static void assertDeducted(Balance result, BigDecimal originalAmount, BigDecimal deductAmount) {
            assertBalanceIntegrity(result);
            assertThat(result.getAmount())
                .as("차감 후 잔액이 정확해야 함")
                .isEqualByComparingTo(originalAmount.subtract(deductAmount));
        }
    }

    /**
     * Coupon 비즈니스 검증
     */
    public static class CouponAssertions {
        
        /**
         * 쿠폰이 유효한 상태인지 검증
         */
        public static void assertCouponValid(Coupon coupon) {
            assertThat(coupon).as("쿠폰은 null이 될 수 없음").isNotNull();
            assertThat(coupon.getId()).as("쿠폰 ID는 양수여야 함").isPositive();
            assertThat(coupon.getCode()).as("쿠폰 코드는 비어있으면 안됨").isNotBlank();
            assertThat(coupon.getDiscountRate()).as("할인율은 0 이상이어야 함").isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(coupon.getMaxIssuance()).as("최대 발급 수량은 양수여야 함").isPositive();
            assertThat(coupon.getIssuedCount()).as("발급된 수량은 음수가 될 수 없음").isGreaterThanOrEqualTo(0);
        }
        
        /**
         * 쿠폰 저장 후 검증
         */
        public static void assertSavedCorrectly(Coupon saved, Coupon expected) {
            assertCouponValid(saved);
            assertThat(saved.getCode()).as("코드가 일치해야 함").isEqualTo(expected.getCode());
            assertThat(saved.getDiscountRate()).as("할인율이 일치해야 함").isEqualByComparingTo(expected.getDiscountRate());
            assertThat(saved.getMaxIssuance()).as("최대 발급 수량이 일치해야 함").isEqualTo(expected.getMaxIssuance());
        }
        
        /**
         * 쿠폰 만료 여부 검증
         */
        public static void assertCouponExpired(Coupon coupon) {
            assertThat(coupon.getEndDate()).as("종료일이 현재 시간보다 이전이어야 함").isBefore(LocalDateTime.now());
        }
        
        /**
         * 쿠폰이 발급 가능한 상태인지 검증
         */
        public static void assertCouponIssuable(Coupon coupon) {
            assertCouponValid(coupon);
            assertThat(coupon.getIssuedCount()).as("발급 가능 수량이 남아있어야 함").isLessThan(coupon.getMaxIssuance());
            assertThat(coupon.getStartDate()).as("시작 시간이 되었어야 함").isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(coupon.getEndDate()).as("아직 만료되지 않았어야 함").isAfterOrEqualTo(LocalDateTime.now());
        }
    }

    /**
     * Order 비즈니스 검증
     */
    public static class OrderAssertions {
        
        /**
         * 주문이 유효한 상태인지 검증
         */
        public static void assertOrderValid(Order order) {
            assertThat(order).as("주문은 null이 될 수 없음").isNotNull();
            assertThat(order.getId()).as("주문 ID는 양수여야 함").isPositive();
            assertThat(order.getUserId()).as("사용자 ID는 양수여야 함").isPositive();
            assertThat(order.getTotalAmount()).as("총 금액은 0보다 커야 함").isPositive();
            assertThat(order.getStatus()).as("주문 상태는 null이 될 수 없음").isNotNull();
        }
        
        /**
         * 주문 저장 후 검증
         */
        public static void assertSavedCorrectly(Order saved, Order expected) {
            assertOrderValid(saved);
            assertThat(saved.getUserId()).as("사용자 ID가 일치해야 함").isEqualTo(expected.getUserId());
            assertThat(saved.getTotalAmount()).as("총 금액이 일치해야 함").isEqualByComparingTo(expected.getTotalAmount());
            assertThat(saved.getStatus()).as("상태가 일치해야 함").isEqualTo(expected.getStatus());
        }
        
        /**
         * 주문이 결제 대기 상태인지 검증
         */
        public static void assertOrderPending(Order order) {
            assertOrderValid(order);
            assertThat(order.getStatus()).as("주문이 결제 대기 상태여야 함")
                .isEqualTo(kr.hhplus.be.server.domain.enums.OrderStatus.PENDING);
        }
        
        /**
         * 주문이 결제 완료 상태인지 검증
         */
        public static void assertOrderPaid(Order order) {
            assertOrderValid(order);
            assertThat(order.getStatus()).as("주문이 결제 완료 상태여야 함")
                .isEqualTo(kr.hhplus.be.server.domain.enums.OrderStatus.PAID);
        }
    }

    /**
     * CouponHistory 비즈니스 검증
     */
    public static class CouponHistoryAssertions {
        
        /**
         * 쿠폰 히스토리가 유효한 상태인지 검증
         */
        public static void assertCouponHistoryValid(CouponHistory history) {
            assertThat(history).as("쿠폰 히스토리는 null이 될 수 없음").isNotNull();
            assertThat(history.getId()).as("히스토리 ID는 양수여야 함").isPositive();
            assertThat(history.getUserId()).as("사용자 ID는 양수여야 함").isPositive();
            assertThat(history.getCouponId()).as("쿠폰 ID는 양수여야 함").isPositive();
            assertThat(history.getIssuedAt()).as("발급 시간은 null이 될 수 없음").isNotNull();
            assertThat(history.getStatus()).as("상태는 null이 될 수 없음").isNotNull();
        }
        
        /**
         * 쿠폰 히스토리 저장 후 검증
         */
        public static void assertSavedCorrectly(CouponHistory saved, CouponHistory expected) {
            assertCouponHistoryValid(saved);
            assertThat(saved.getUserId()).as("사용자 ID가 일치해야 함").isEqualTo(expected.getUserId());
            assertThat(saved.getCouponId()).as("쿠폰 ID가 일치해야 함").isEqualTo(expected.getCouponId());
            assertThat(saved.getStatus()).as("상태가 일치해야 함").isEqualTo(expected.getStatus());
        }
        
        /**
         * 쿠폰이 발급된 상태인지 검증
         */
        public static void assertCouponIssued(CouponHistory history) {
            assertCouponHistoryValid(history);
            assertThat(history.getStatus()).as("쿠폰이 발급 상태여야 함")
                .isEqualTo(kr.hhplus.be.server.domain.enums.CouponHistoryStatus.ISSUED);
        }
        
        /**
         * 쿠폰이 사용된 상태인지 검증
         */
        public static void assertCouponUsed(CouponHistory history) {
            assertCouponHistoryValid(history);
            assertThat(history.getStatus()).as("쿠폰이 사용 상태여야 함")
                .isEqualTo(kr.hhplus.be.server.domain.enums.CouponHistoryStatus.USED);
        }
    }

    /**
     * 도메인 공통 검증 유틸리티
     * 너무 구체적이지 않고 범용적으로 사용 가능한 것들
     */
    public static class CommonAssertions {
        
        /**
         * 엔티티의 기본적인 유효성만 검증
         */
        public static void assertEntityValid(Object entity) {
            assertThat(entity).as("엔티티는 null이 될 수 없음").isNotNull();
        }
        
        /**
         * 리스트 응답의 기본 검증
         */
        public static void assertListNotEmpty(List<?> list) {
            assertThat(list)
                .as("리스트는 null이 아니고 비어있지 않아야 함")
                .isNotNull()
                .isNotEmpty();
        }
        
        /**
         * 금액 관련 기본 검증
         */
        public static void assertAmountPositive(BigDecimal amount) {
            assertThat(amount)
                .as("금액은 양수여야 함")
                .isPositive();
        }
        
        /**
         * ID 값 기본 검증  
         */
        public static void assertIdValid(Long id) {
            assertThat(id)
                .as("ID는 양수여야 함")
                .isPositive();
        }
    }
}