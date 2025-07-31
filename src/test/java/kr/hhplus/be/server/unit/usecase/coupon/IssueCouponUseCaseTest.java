package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.TestConstants;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;

import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;

@DisplayName("IssueCouponUseCase 단위 테스트")
class IssueCouponUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    

    private IssueCouponUseCase issueCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        issueCouponUseCase = new IssueCouponUseCase(
                userRepositoryPort, couponRepositoryPort, couponHistoryRepositoryPort
        );
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        Coupon coupon = Coupon.builder()
                .code(TestConstants.DISCOUNT_COUPON_CODE)
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)).thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CouponHistory result = issueCouponUseCase.execute(userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getCoupon()).isEqualTo(coupon);
        assertThat(result.getIssuedAt()).isNotNull();
        
        verify(couponRepositoryPort).save(coupon);
        verify(couponHistoryRepositoryPort).save(any(CouponHistory.class));
    }

    @ParameterizedTest
    @MethodSource("provideCouponData")
    @DisplayName("다양한 쿠폰 발급 시나리오")
    void issueCoupon_WithDifferentScenarios(Long userId, Long couponId, String couponCode) {
        // given
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        Coupon coupon = Coupon.builder()
                .code(couponCode)
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(200)
                .issuedCount(100)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(15))
                .status(CouponStatus.ACTIVE)
                .build();
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)).thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CouponHistory result = issueCouponUseCase.execute(userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getCoupon().getCode()).isEqualTo(couponCode);
        
        
    }

    @Test
    @DisplayName("존재하지 않는 사용자 쿠폰 발급 시 예외 발생")
    void issueCoupon_UserNotFound() {
        // given
        Long userId = 999L;
        Long couponId = 1L;
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(UserException.NotFound.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
                

    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외 발생")
    void issueCoupon_CouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 999L;
        
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(ErrorCode.COUPON_NOT_FOUND.getMessage());
                

    }

        @Test
        @DisplayName("실패케이스: 만료된 쿠폰 발급")
        void issueCoupon_ExpiredCoupon() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        Coupon expiredCoupon = Coupon.builder()
                .code(TestConstants.EXPIRED_COUPON_CODE)
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1))
                .status(CouponStatus.EXPIRED)
                .build();
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(expiredCoupon));

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.Expired.class)
                .hasMessage(ErrorCode.COUPON_EXPIRED.getMessage());
                

    }

    @Test
    @DisplayName("재고 소진된 쿠폰 발급 시 예외 발생")
    void issueCoupon_OutOfStock() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        Coupon outOfStockCoupon = Coupon.builder()
                .code(TestConstants.OUT_OF_STOCK_COUPON_CODE)
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(100)
                .issuedCount(100)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.SOLD_OUT)
                .build();
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(outOfStockCoupon));

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.OutOfStock.class)
                .hasMessage(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getMessage());
                

    }

    @Test
    @DisplayName("이미 발급받은 쿠폰 재발급 시 예외 발생")
    void issueCoupon_AlreadyIssued() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("ALREADY_ISSUED")
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserAndCoupon(user, coupon)).thenReturn(true);
        
        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.AlreadyIssued.class)
                .hasMessage(ErrorCode.COUPON_ALREADY_ISSUED.getMessage());
                

    }

    @Test
    @DisplayName("null 사용자 ID로 쿠폰 발급 시 예외 발생")
    void issueCoupon_WithNullUserId() {
        // given
        Long userId = null;
        Long couponId = 1L;

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 쿠폰 ID로 쿠폰 발급 시 예외 발생")
    void issueCoupon_WithNullCouponId() {
        // given
        Long userId = 1L;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("아직 시작되지 않은 쿠폰 발급 시 예외 발생")
    void issueCoupon_NotYetStarted() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name(TestConstants.TEST_USER_NAME)
                .build();
        
        Coupon futureStartCoupon = Coupon.builder()
                .code("FUTURE_START")
                .discountRate(new BigDecimal("0.25"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.INACTIVE)
                .build();
        
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(futureStartCoupon));
        
        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.CouponNotYetStarted.class)
                .hasMessage(ErrorCode.COUPON_NOT_YET_STARTED.getMessage());
                

    }

    private static Stream<Arguments> provideCouponData() {
        return Stream.of(
                Arguments.of(1L, 1L, "WELCOME10"),
                Arguments.of(2L, 2L, "SUMMER25"),
                Arguments.of(3L, 3L, "VIP30")
        );
    }

    private static Stream<Arguments> provideInvalidIds() {
        return Stream.of(
                Arguments.of(999L, 999L), // 존재하지 않는 ID들
                Arguments.of(888L, 888L)
        );
    }
}