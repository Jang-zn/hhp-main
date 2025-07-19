package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryCouponHistoryRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Nested
    @DisplayName("쿠폰 히스토리 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 쿠폰 히스토리 저장")
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

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryCouponHistoryRepositoryTest#provideCouponHistoryData")
        @DisplayName("성공케이스: 다양한 쿠폰 히스토리 데이터로 저장")
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
    }

    @Nested
    @DisplayName("쿠폰 히스토리 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 사용자 ID와 쿠폰 ID로 히스토리 존재 여부 확인")
        void existsByUserAndCoupon_Success() {
        // given
        User user = User.builder().id(1L).name("테스트 사용자").build();
        Coupon coupon = Coupon.builder().id(1L).code("SALE20").discountRate(new BigDecimal("0.20")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();
        CouponHistory couponHistory = CouponHistory.builder().id(1L).user(user).coupon(coupon).issuedAt(LocalDateTime.now()).build();
        couponHistoryRepository.save(couponHistory);

        // when
        boolean exists = couponHistoryRepository.existsByUserAndCoupon(user, coupon);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("성공케이스: 사용자 ID로 쿠폰 히스토리 목록 조회")
        void findByUserWithPagination_Success() {
        // given
        User user = User.builder().id(1L).name("테스트 사용자").build();
        Coupon coupon1 = Coupon.builder().id(1L).code("SALE10").discountRate(new BigDecimal("0.10")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();
        Coupon coupon2 = Coupon.builder().id(2L).code("SALE20").discountRate(new BigDecimal("0.20")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();
        couponHistoryRepository.save(CouponHistory.builder().id(1L).user(user).coupon(coupon1).issuedAt(LocalDateTime.now()).build());
        couponHistoryRepository.save(CouponHistory.builder().id(2L).user(user).coupon(coupon2).issuedAt(LocalDateTime.now()).build());

        // when
        List<CouponHistory> histories = couponHistoryRepository.findByUserWithPagination(user, 1, 0);

            // then
            assertThat(histories).hasSize(1);
            assertThat(histories.get(0).getCoupon().getCode()).isEqualTo("SALE10");
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 쿠폰 히스토리 조회")
        void existsByUserAndCoupon_NotFound() {
            // given
            User user = User.builder().id(999L).name("비존재 사용자").build();
            Coupon coupon = Coupon.builder().id(999L).code("NOTFOUND").discountRate(new BigDecimal("0.10")).maxIssuance(100).issuedCount(0).startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30)).build();

            // when
            boolean exists = couponHistoryRepository.existsByUserAndCoupon(user, coupon);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("실패케이스: 빈 페이지 조회")
        void findByUserWithPagination_EmptyResult() {
            // given
            User user = User.builder().id(999L).name("데이터 없는 사용자").build();

            // when
            List<CouponHistory> histories = couponHistoryRepository.findByUserWithPagination(user, 10, 0);

            // then
            assertThat(histories).isEmpty();
        }
    }

    private static Stream<Arguments> provideCouponHistoryData() {
        return Stream.of(
                Arguments.of("홍길동", "WELCOME10"),
                Arguments.of("김철수", "SUMMER25"),
                Arguments.of("이영희", "VIP30")
        );
    }
}