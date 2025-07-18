package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryCouponRepository 단위 테스트")
class InMemoryCouponRepositoryTest {

    private InMemoryCouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
    }

    @Test
    @DisplayName("쿠폰 저장 성공")
    void save_Success() {
        // given
        Product product = Product.builder()
                .name("노트북")
                .price(new BigDecimal("1200000"))
                .stock(10)
                .reservedStock(0)
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .product(product)
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);

        // then
        assertThat(savedCoupon).isNotNull();
        assertThat(savedCoupon.getCode()).isEqualTo("DISCOUNT10");
        assertThat(savedCoupon.getDiscountRate()).isEqualTo(new BigDecimal("0.10"));
        assertThat(savedCoupon.getMaxIssuance()).isEqualTo(100);
    }

    @Test
    @DisplayName("쿠폰 ID로 조회 성공")
    void findById_Success() {
        // given
        Coupon coupon = Coupon.builder()
                .code("SALE20")
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(50)
                .issuedCount(10)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(15))
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // when
        Optional<Coupon> foundCoupon = couponRepository.findById(savedCoupon.getId());

        // then
        assertThat(foundCoupon).isPresent();
        assertThat(foundCoupon.get().getCode()).isEqualTo("SALE20");
        assertThat(foundCoupon.get().getDiscountRate()).isEqualTo(new BigDecimal("0.20"));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 조회")
    void findById_NotFound() {
        // when
        Optional<Coupon> foundCoupon = couponRepository.findById(999L);

        // then
        assertThat(foundCoupon).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideCouponData")
    @DisplayName("다양한 쿠폰 데이터로 저장")
    void save_WithDifferentCouponData(String code, String discountRate, int maxIssuance) {
        // given
        Coupon coupon = Coupon.builder()
                .code(code)
                .discountRate(new BigDecimal(discountRate))
                .maxIssuance(maxIssuance)
                .issuedCount(0)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);

        // then
        assertThat(savedCoupon).isNotNull();
        assertThat(savedCoupon.getCode()).isEqualTo(code);
        assertThat(savedCoupon.getDiscountRate()).isEqualTo(new BigDecimal(discountRate));
        assertThat(savedCoupon.getMaxIssuance()).isEqualTo(maxIssuance);
    }

    private static Stream<Arguments> provideCouponData() {
        return Stream.of(
                Arguments.of("WELCOME10", "0.10", 1000),
                Arguments.of("SUMMER25", "0.25", 500),
                Arguments.of("VIP30", "0.30", 100)
        );
    }
}