package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.domain.usecase.coupon.AcquireCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponController 단위 테스트")
class CouponControllerTest {

    private CouponController couponController;
    private AcquireCouponUseCase acquireCouponUseCase;
    private GetCouponListUseCase getCouponListUseCase;

    @BeforeEach
    void setUp() {
        acquireCouponUseCase = new AcquireCouponUseCase(null, null, null, null);
        getCouponListUseCase = new GetCouponListUseCase(null, null, null);
        couponController = new CouponController(acquireCouponUseCase, getCouponListUseCase);
    }

    @Nested
    @DisplayName("쿠폰 발급 테스트")
    class AcquireCouponTests {
        
        @Test
        @DisplayName("성공케이스: 정상 쿠폰 발급")
        void acquireCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        // when
        CouponRequest request = new CouponRequest(userId, couponId);
        CouponResponse response = couponController.acquireCoupon(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.code()).isEqualTo("COUPON123");
        assertThat(response.discountRate()).isEqualTo(new BigDecimal("0.1"));
        assertThat(response.validUntil()).isNotNull();
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.controller.CouponControllerTest#provideCouponData")
        @DisplayName("성공케이스: 다양한 쿠폰으로 발급 테스트")
        void acquireCoupon_WithDifferentCoupons(Long userId, Long couponId) {
            // when
            CouponRequest request = new CouponRequest(userId, couponId);
            CouponResponse response = couponController.acquireCoupon(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.couponId()).isEqualTo(couponId);
            assertThat(response.code()).isNotNull();
            assertThat(response.discountRate()).isNotNull();
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자로 쿠폰 발급")
        void acquireCoupon_UserNotFound() {
            // given
            Long invalidUserId = 999L;
            Long couponId = 1L;
            CouponRequest request = new CouponRequest(invalidUserId, couponId);

            // when & then
            assertThatThrownBy(() -> couponController.acquireCoupon(request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 쿠폰 발급")
        void acquireCoupon_CouponNotFound() {
            // given
            Long userId = 1L;
            Long invalidCouponId = 999L;
            CouponRequest request = new CouponRequest(userId, invalidCouponId);

            // when & then
            assertThatThrownBy(() -> couponController.acquireCoupon(request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("실패케이스: null 요청으로 쿠폰 발급")
        void acquireCoupon_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> couponController.acquireCoupon(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("보유 쿠폰 조회 테스트")
    class GetCouponsTests {
        
        @Test
        @DisplayName("성공케이스: 정상 보유 쿠폰 조회")
        void getCoupons_Success() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;

        // when
        CouponRequest request = new CouponRequest(limit, offset);
        List<CouponResponse> response = couponController.getCoupons(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
        assertThat(response.get(0).code()).isEqualTo("COUPON123");
        assertThat(response.get(1).code()).isEqualTo("COUPON456");
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.controller.CouponControllerTest#providePaginationData")
        @DisplayName("성공케이스: 다양한 페이지네이션으로 쿠폰 조회")
        void getCoupons_WithDifferentPagination(Long userId, int limit, int offset) {
        // when
        CouponRequest request = new CouponRequest(limit, offset);
        List<CouponResponse> response = couponController.getCoupons(userId, request);

        // then
            assertThat(response).isNotNull();
            assertThat(response).isNotEmpty();
            assertThat(response).hasSizeLessThanOrEqualTo(limit);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자의 쿠폰 조회")
        void getCoupons_UserNotFound() {
            // given
            Long invalidUserId = 999L;
            CouponRequest request = new CouponRequest(10, 0);

            // when & then
            assertThatThrownBy(() -> couponController.getCoupons(invalidUserId, request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("실패케이스: null 사용자 ID로 쿠폰 조회")
        void getCoupons_WithNullUserId() {
            // given
            CouponRequest request = new CouponRequest(10, 0);

            // when & then
            assertThatThrownBy(() -> couponController.getCoupons(null, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패케이스: 비정상 페이지네이션 파라미터")
        void getCoupons_WithInvalidPagination() {
            // given
            Long userId = 1L;
            CouponRequest invalidRequest = new CouponRequest(-1, -1);

            // when & then
            assertThatThrownBy(() -> couponController.getCoupons(userId, invalidRequest))
                    .isInstanceOf(IllegalArgumentException.class);
        }
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

    private static Stream<Arguments> provideInvalidCouponData() {
        return Stream.of(
                Arguments.of(null, 1L),
                Arguments.of(1L, null),
                Arguments.of(-1L, 1L),
                Arguments.of(1L, -1L)
        );
    }
}