package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IssueCouponUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 쿠폰 발급 유스케이스의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 쿠폰 발급 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("쿠폰 발급 유스케이스 비즈니스 시나리오")
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

    // === 성공적인 쿠폰 발급 시나리오 ===
    
    @Test
    @DisplayName("유효한 쿠폰을 정상적으로 발급할 수 있다")
    void canIssueCouponSuccessfully() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = TestBuilder.UserBuilder.defaultUser().id(userId).build();
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .code("WELCOME10")
                .discountRate(new BigDecimal("0.10"))
                .withQuantity(100, 50)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findByIdWithLock(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CouponHistory result = issueCouponUseCase.execute(userId, couponId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        assertThat(result.getIssuedAt()).isNotNull();
        
        verify(couponRepositoryPort).save(coupon);
        verify(couponHistoryRepositoryPort).save(any(CouponHistory.class));
    }

    @ParameterizedTest
    @MethodSource("provideCouponData")
    @DisplayName("다양한 쿠폰을 성공적으로 발급할 수 있다")
    void canIssueVariousCoupons(Long userId, Long couponId, String couponCode, String discountRate) {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser().id(userId).build();
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .code(couponCode)
                .discountRate(new BigDecimal(discountRate))
                .withQuantity(200, 100)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findByIdWithLock(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(couponRepositoryPort.save(any(Coupon.class))).thenReturn(coupon);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CouponHistory result = issueCouponUseCase.execute(userId, couponId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCouponId()).isEqualTo(couponId);
        assertThat(result.getUserId()).isEqualTo(userId);
    }

    // === 예외 처리 시나리오 ===
    
    @Test
    @DisplayName("존재하지 않는 사용자에게 쿠폰 발급 시 예외가 발생한다")
    void throwsExceptionWhenUserNotFound() {
        // Given
        Long userId = 999L;
        Long couponId = 1L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(UserException.NotFound.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외가 발생한다")
    void throwsExceptionWhenCouponNotFound() {
        // Given
        Long userId = 1L;
        Long couponId = 999L;
        
        User user = TestBuilder.UserBuilder.defaultUser().id(userId).build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findByIdWithLock(couponId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(ErrorCode.COUPON_NOT_FOUND.getMessage());
    }

    @ParameterizedTest
    @MethodSource("provideInvalidCouponScenarios")
    @DisplayName("쿠폰 발급 불가 조건에서 적절한 예외가 발생한다")
    void throwsExceptionForInvalidCouponScenarios(String description, Coupon invalidCoupon, 
                                                  Class<? extends Exception> expectedException, 
                                                  String expectedMessage) {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = TestBuilder.UserBuilder.defaultUser().id(userId).build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findByIdWithLock(couponId)).thenReturn(Optional.of(invalidCoupon));
        if (!description.contains("already")) {
            when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(description.contains("already"));
        } else {
            when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(true);
        }

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(expectedException)
                .hasMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("provideNullParameterScenarios")
    @DisplayName("null 매개변수로 쿠폰 발급 시 예외가 발생한다")
    void throwsExceptionForNullParameters(String description, Long userId, Long couponId) {
        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(description.contains("user") ? "User" : "Coupon");
    }

    @Test
    @DisplayName("아직 시작되지 않은 쿠폰 발급 시 예외가 발생한다")
    void throwsExceptionForNotYetStartedCoupon() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = TestBuilder.UserBuilder.defaultUser().id(userId).build();
        Coupon futureStartCoupon = TestBuilder.CouponBuilder.notYetStartedCoupon()
                .id(couponId)
                .code("FUTURE_START")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findByIdWithLock(couponId)).thenReturn(Optional.of(futureStartCoupon));
        
        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.CouponNotYetStarted.class)
                .hasMessage(ErrorCode.COUPON_NOT_YET_STARTED.getMessage());
    }

    // === 헬퍼 메서드 ===
    
    static Stream<Arguments> provideCouponData() {
        return Stream.of(
                Arguments.of(1L, 1L, "WELCOME10", "0.10"),
                Arguments.of(2L, 2L, "SUMMER25", "0.25"),
                Arguments.of(3L, 3L, "VIP30", "0.30"),
                Arguments.of(4L, 4L, "BLACK_FRIDAY", "0.50")
        );
    }

    static Stream<Arguments> provideInvalidCouponScenarios() {
        return Stream.of(
                Arguments.of("expired coupon", 
                    TestBuilder.CouponBuilder.expiredCoupon().build(),
                    CouponException.Expired.class,
                    ErrorCode.COUPON_EXPIRED.getMessage()),
                Arguments.of("sold out coupon",
                    TestBuilder.CouponBuilder.soldOutCoupon().build(),
                    CouponException.OutOfStock.class,
                    ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getMessage()),
                Arguments.of("already issued",
                    TestBuilder.CouponBuilder.defaultCoupon().code("ALREADY_ISSUED").build(),
                    CouponException.AlreadyIssued.class,
                    ErrorCode.COUPON_ALREADY_ISSUED.getMessage())
        );
    }
    
    static Stream<Arguments> provideNullParameterScenarios() {
        return Stream.of(
                Arguments.of("null user ID", null, 1L),
                Arguments.of("null coupon ID", 1L, null)
        );
    }
}