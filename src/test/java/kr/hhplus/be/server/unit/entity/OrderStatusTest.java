package kr.hhplus.be.server.unit.entity;

import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.OrderStatus;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Order 엔티티 상태 테스트")
class OrderStatusTest {

    @Test
    @DisplayName("Order 생성 시 기본 상태는 PENDING")
    void testOrderDefaultStatus() {
        // given
        User user = User.builder().name("테스트 사용자").build();
        
        // when
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Order 상태 설정 확인")
    void testOrderStatusSetting() {
        // given
        User user = User.builder().name("테스트 사용자").build();
        
        // when
        Order order = Order.builder()
                .user(user)
                .totalAmount(new BigDecimal("100000"))
                .status(OrderStatus.PAID)
                .build();
        
        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("OrderStatus enum 값 확인")
    void testOrderStatusValues() {
        assertThat(OrderStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(OrderStatus.PAID.name()).isEqualTo("PAID");
        assertThat(OrderStatus.CANCELLED.name()).isEqualTo("CANCELLED");
        assertThat(OrderStatus.COMPLETED.name()).isEqualTo("COMPLETED");
    }
}