package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponHistoryRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
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

@DisplayName("InMemoryCouponHistoryRepository 단위 테스트")
class InMemoryCouponHistoryRepositoryTest {

    private InMemoryCouponHistoryRepository couponHistoryRepository;

    @BeforeEach
    void setUp() {
        couponHistoryRepository = new InMemoryCouponHistoryRepository();
    }

    @Test
    @DisplayName("쿠폰 히스토리 저장 성공")
    void save_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(1)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        CouponHistory couponHistory = CouponHistory.builder()
                .user(user)
                .coupon(coupon)
                .issuedAt(LocalDateTime.now())
                .build();

        // when
        CouponHistory savedHistory = couponHistoryRepository.save(couponHistory);

        // then
        assertThat(savedHistory).isNotNull();
        assertThat(savedHistory.getUser()).isEqualTo(user);
        assertThat(savedHistory.getCoupon()).isEqualTo(coupon);
        assertThat(savedHistory.getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("쿠폰 히스토리 ID로 조회 성공")
    void findById_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("SALE20")
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(50)
                .issuedCount(1)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(15))
                .build();
        
        CouponHistory couponHistory = CouponHistory.builder()
                .user(user)
                .coupon(coupon)
                .issuedAt(LocalDateTime.now())
                .build();
        CouponHistory savedHistory = couponHistoryRepository.save(couponHistory);

        // when
        Optional<CouponHistory> foundHistory = couponHistoryRepository.findById(savedHistory.getId());

        // then
        assertThat(foundHistory).isPresent();
        assertThat(foundHistory.get().getUser()).isEqualTo(user);
        assertThat(foundHistory.get().getCoupon().getCode()).isEqualTo("SALE20");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 히스토리 조회")
    void findById_NotFound() {
        // when
        Optional<CouponHistory> foundHistory = couponHistoryRepository.findById(999L);

        // then
        assertThat(foundHistory).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideCouponHistoryData")
    @DisplayName("다양한 쿠폰 히스토리 데이터로 저장")
    void save_WithDifferentHistoryData(String userName, String couponCode) {
        // given
        User user = User.builder()
                .name(userName)
                .build();
        
        Coupon coupon = Coupon.builder()
                .code(couponCode)
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(200)
                .issuedCount(1)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        CouponHistory couponHistory = CouponHistory.builder()
                .user(user)
                .coupon(coupon)
                .issuedAt(LocalDateTime.now())
                .build();

        // when
        CouponHistory savedHistory = couponHistoryRepository.save(couponHistory);

        // then
        assertThat(savedHistory).isNotNull();
        assertThat(savedHistory.getUser().getName()).isEqualTo(userName);
        assertThat(savedHistory.getCoupon().getCode()).isEqualTo(couponCode);
    }

    private static Stream<Arguments> provideCouponHistoryData() {
        return Stream.of(
                Arguments.of("홍길동", "WELCOME10"),
                Arguments.of("김철수", "SUMMER25"),
                Arguments.of("이영희", "VIP30")
        );
    }
}