package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.domain.usecase.coupon.AcquireCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CouponController 단위 테스트")
class CouponControllerTest {

    private CouponController couponController;
    private AcquireCouponUseCase acquireCouponUseCase;
    private GetCouponListUseCase getCouponListUseCase;

    @BeforeEach
    void setUp() {
        acquireCouponUseCase = new AcquireCouponUseCase(null, null, null, null);
        getCouponListUseCase = new GetCouponListUseCase(null, null);
        couponController = new CouponController(acquireCouponUseCase, getCouponListUseCase);
    }

    @Test
    @DisplayName("쿠폰 발급 API 성공")
    void acquireCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        // when
        CouponResponse response = couponController.acquireCoupon(userId, couponId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.code()).isEqualTo("COUPON123");
        assertThat(response.discountRate()).isEqualTo(new BigDecimal("0.1"));
        assertThat(response.validUntil()).isNotNull();
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API 성공")
    void getCoupons_Success() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;

        // when
        List<CouponResponse> response = couponController.getCoupons(userId, limit, offset);

        // then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
        assertThat(response.get(0).code()).isEqualTo("COUPON123");
        assertThat(response.get(1).code()).isEqualTo("COUPON456");
    }

    @ParameterizedTest
    @MethodSource("provideCouponData")
    @DisplayName("다양한 쿠폰으로 발급 테스트")
    void acquireCoupon_WithDifferentCoupons(Long userId, Long couponId) {
        // when
        CouponResponse response = couponController.acquireCoupon(userId, couponId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.code()).isNotNull();
        assertThat(response.discountRate()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("providePaginationData")
    @DisplayName("다양한 페이지네이션으로 쿠폰 조회")
    void getCoupons_WithDifferentPagination(Long userId, int limit, int offset) {
        // when
        List<CouponResponse> response = couponController.getCoupons(userId, limit, offset);

        // then
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response).hasSizeLessThanOrEqualTo(limit);
    }

    private static Stream<Arguments> provideCouponData() {
        return Stream.of(
                Arguments.of(1L, 1L),
                Arguments.of(2L, 2L),
                Arguments.of(3L, 3L)
        );
    }

    private static Stream<Arguments> providePaginationData() {
        return Stream.of(
                Arguments.of(1L, 5, 0),
                Arguments.of(2L, 10, 5),
                Arguments.of(3L, 20, 0)
        );
    }
}