package kr.hhplus.be.server.util;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.dto.ProductQuantityDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 테스트용 엔티티 빌더 팩토리 클래스
 * 
 * Why: 테스트 데이터 생성의 일관성과 재사용성을 위해 중앙화된 빌더 제공
 * How: 각 도메인별 기본값을 제공하며 필요한 부분만 커스터마이징 가능한 빌더 패턴
 */
public class TestBuilder {

    /**
     * Product 테스트 빌더 - 체이닝 패턴으로 더 fluent하게 구성
     */
    public static class ProductBuilder {
        private Long id = 1L;
        private String name = "테스트 상품";
        private BigDecimal price = BigDecimal.valueOf(10000);
        private int stock = 100;
        private int reservedStock = 0;

        public static ProductBuilder defaultProduct() {
            return new ProductBuilder();
        }

        public static ProductBuilder outOfStockProduct() {
            return new ProductBuilder().stock(0).reservedStock(0);
        }

        public static ProductBuilder partiallyReservedProduct() {
            return new ProductBuilder().stock(100).reservedStock(30);
        }

        public ProductBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ProductBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ProductBuilder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public ProductBuilder stock(int stock) {
            this.stock = stock;
            return this;
        }

        public ProductBuilder reservedStock(int reservedStock) {
            this.reservedStock = reservedStock;
            return this;
        }

        public ProductBuilder withStock(int total, int reserved) {
            this.stock = total;
            this.reservedStock = reserved;
            return this;
        }

        public Product build() {
            return Product.builder()
                .id(id)
                .name(name)
                .price(price)
                .stock(stock)
                .reservedStock(reservedStock)
                .build();
        }
    }

    /**
     * Coupon 테스트 빌더 - 개선된 메서드 체이닝
     */
    public static class CouponBuilder {
        private Long id = 1L;
        private String code = "TEST_COUPON";
        private BigDecimal discountRate = BigDecimal.valueOf(0.1);
        private Integer maxIssuance = 100;
        private Integer issuedCount = 0;
        private LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        private LocalDateTime endDate = LocalDateTime.now().plusDays(7);

        public static CouponBuilder defaultCoupon() {
            return new CouponBuilder();
        }

        public static CouponBuilder expiredCoupon() {
            return new CouponBuilder()
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(3));
        }

        public static CouponBuilder notYetStartedCoupon() {
            return new CouponBuilder()
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(7));
        }

        public static CouponBuilder soldOutCoupon() {
            return new CouponBuilder().withQuantity(10, 10);
        }

        public CouponBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CouponBuilder code(String code) {
            this.code = code;
            return this;
        }

        public CouponBuilder discountRate(BigDecimal discountRate) {
            this.discountRate = discountRate;
            return this;
        }

        public CouponBuilder withQuantity(int max, int issued) {
            this.maxIssuance = max;
            this.issuedCount = issued;
            return this;
        }

        public CouponBuilder startDate(LocalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public CouponBuilder endDate(LocalDateTime endDate) {
            this.endDate = endDate;
            return this;
        }

        public Coupon build() {
            return Coupon.builder()
                .id(id)
                .code(code)
                .discountRate(discountRate)
                .maxIssuance(maxIssuance)
                .issuedCount(issuedCount)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        }
    }

    /**
     * Balance 테스트 빌더 - 간소화된 패턴
     */
    public static class BalanceBuilder {
        private Long id = 1L;
        private Long userId = 1L;
        private BigDecimal amount = BigDecimal.valueOf(100000);

        public static BalanceBuilder defaultBalance() {
            return new BalanceBuilder();
        }

        public static BalanceBuilder emptyBalance() {
            return new BalanceBuilder().amount(BigDecimal.ZERO);
        }

        public static BalanceBuilder insufficientBalance() {
            return new BalanceBuilder().amount(BigDecimal.valueOf(1000));
        }

        public BalanceBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public BalanceBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public BalanceBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Balance build() {
            return Balance.builder()
                .id(id)
                .userId(userId)
                .amount(amount)
                .build();
        }
    }

    /**
     * User 테스트 빌더
     */
    public static class UserBuilder {
        private Long id = 1L;
        private String name = "테스트 사용자";

        public static UserBuilder defaultUser() {
            return new UserBuilder();
        }

        public UserBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public UserBuilder name(String name) {
            this.name = name;
            return this;
        }

        public User build() {
            return User.builder()
                .id(id)
                .name(name)
                .build();
        }
    }

    /**
     * Order 테스트 빌더
     */
    public static class OrderBuilder {
        private Long id = 1L;
        private Long userId = 1L;
        private BigDecimal totalAmount = BigDecimal.valueOf(50000);
        private kr.hhplus.be.server.domain.enums.OrderStatus status = kr.hhplus.be.server.domain.enums.OrderStatus.PENDING;

        public static OrderBuilder defaultOrder() {
            return new OrderBuilder();
        }

        public static OrderBuilder paidOrder() {
            return new OrderBuilder()
                .status(kr.hhplus.be.server.domain.enums.OrderStatus.PAID);
        }

        public OrderBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public OrderBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public OrderBuilder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public OrderBuilder status(kr.hhplus.be.server.domain.enums.OrderStatus status) {
            this.status = status;
            return this;
        }

        public Order build() {
            return Order.builder()
                .id(id)
                .userId(userId)
                .totalAmount(totalAmount)
                .status(status)
                .build();
        }
    }

    /**
     * ProductQuantityDto 테스트 빌더
     */
    public static class ProductQuantityBuilder {
        private Long productId = 1L;
        private int quantity = 1;

        public static ProductQuantityBuilder defaultProductQuantity() {
            return new ProductQuantityBuilder();
        }

        public ProductQuantityBuilder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public ProductQuantityBuilder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public ProductQuantityDto build() {
            return new ProductQuantityDto(productId, quantity);
        }
    }
}