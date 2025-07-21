package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponController 단위 테스트")
class CouponControllerTest {

    private CouponController couponController;
    
    @Mock
    private IssueCouponUseCase issueCouponUseCase;
    
    @Mock
    private GetCouponListUseCase getCouponListUseCase;

    @BeforeEach
    void setUp() {
        couponController = new CouponController(issueCouponUseCase, getCouponListUseCase);
    }

    @Nested
    @DisplayName("쿠폰 발급 테스트")
    class IssueCouponTests {
        
        @Test
        @DisplayName("성공케이스: 정상 쿠폰 발급")
        void issueCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        LocalDateTime endDate = LocalDateTime.now().plusDays(30);
        
        Coupon mockCoupon = Coupon.builder()
            .id(couponId)
            .code("COUPON123")
            .discountRate(new BigDecimal("0.1"))
            .endDate(endDate)
            .build();
            
        CouponHistory mockHistory = CouponHistory.builder()
            .coupon(mockCoupon)
            .issuedAt(LocalDateTime.now())
            .build();
            
        when(issueCouponUseCase.execute(userId, couponId)).thenReturn(mockHistory);

        // when
        CouponRequest request = new CouponRequest(userId, couponId);
        CouponResponse response = couponController.issueCoupon(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.code()).isEqualTo("COUPON123");
        assertThat(response.discountRate()).isEqualTo(new BigDecimal("0.1"));
        assertThat(response.validUntil()).isEqualTo(endDate);
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.controller.CouponControllerTest#provideCouponData")
        @DisplayName("성공케이스: 다양한 쿠폰으로 발급 테스트")
        void issueCoupon_WithDifferentCoupons(Long userId, Long couponId) {
            // given
            Coupon mockCoupon = Coupon.builder()
                .id(couponId)
                .code("COUPON" + couponId)
                .discountRate(new BigDecimal("0.15"))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
                
            CouponHistory mockHistory = CouponHistory.builder()
                .coupon(mockCoupon)
                .issuedAt(LocalDateTime.now())
                .build();
                
            when(issueCouponUseCase.execute(userId, couponId)).thenReturn(mockHistory);
            
            // when
            CouponRequest request = new CouponRequest(userId, couponId);
            CouponResponse response = couponController.issueCoupon(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.couponId()).isEqualTo(couponId);
            assertThat(response.code()).isNotNull();
            assertThat(response.discountRate()).isNotNull();
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자로 쿠폰 발급")
        void issueCoupon_UserNotFound() {
            // given
            Long invalidUserId = 999L;
            Long couponId = 1L;
            CouponRequest request = new CouponRequest(invalidUserId, couponId);
            
            when(issueCouponUseCase.execute(invalidUserId, couponId))
                .thenThrow(new RuntimeException(CouponException.Messages.USER_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 쿠폰 발급")
        void issueCoupon_CouponNotFound() {
            // given
            Long userId = 1L;
            Long invalidCouponId = 999L;
            CouponRequest request = new CouponRequest(userId, invalidCouponId);
            
            when(issueCouponUseCase.execute(userId, invalidCouponId))
                .thenThrow(new RuntimeException(CouponException.Messages.COUPON_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("실패케이스: null 요청으로 쿠폰 발급")
        void issueCoupon_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(null))
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
        
        List<CouponHistory> mockHistories = Arrays.asList(
            CouponHistory.builder()
                .coupon(Coupon.builder()
                    .id(1L)
                    .code("COUPON123")
                    .discountRate(new BigDecimal("0.1"))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .build())
                .build(),
            CouponHistory.builder()
                .coupon(Coupon.builder()
                    .id(2L)
                    .code("COUPON456")
                    .discountRate(new BigDecimal("0.2"))
                    .endDate(LocalDateTime.now().plusDays(15))
                    .build())
                .build()
        );
        
        when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(mockHistories);

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
        // given
        List<CouponHistory> mockHistories = Arrays.asList(
            CouponHistory.builder()
                .coupon(Coupon.builder()
                    .id(1L)
                    .code("COUPON123")
                    .discountRate(new BigDecimal("0.1"))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .build())
                .build()
        );
        
        when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(mockHistories);
        
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
            
            when(getCouponListUseCase.execute(invalidUserId, 10, 0))
                .thenThrow(new RuntimeException(CouponException.Messages.USER_NOT_FOUND));

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

}