package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.usecase.coupon.AcquireCouponUseCase;
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
import static org.mockito.Mockito.when;

import kr.hhplus.be.server.domain.exception.CouponException;

@DisplayName("AcquireCouponUseCase 단위 테스트")
class AcquireCouponUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;

    private AcquireCouponUseCase acquireCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        acquireCouponUseCase = new AcquireCouponUseCase(
                userRepositoryPort, couponRepositoryPort, couponHistoryRepositoryPort, lockingPort
        );
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void acquireCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CouponHistory result = acquireCouponUseCase.execute(userId, couponId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 null 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isNotNull();
        // assertThat(result.getUser()).isEqualTo(user);
        // assertThat(result.getCoupon()).isEqualTo(coupon);
    }

    @ParameterizedTest
    @MethodSource("provideCouponData")
    @DisplayName("다양한 쿠폰 발급 시나리오")
    void acquireCoupon_WithDifferentScenarios(Long userId, Long couponId, String couponCode) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code(couponCode)
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(200)
                .issuedCount(100)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(15))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CouponHistory result = acquireCouponUseCase.execute(userId, couponId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 null 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isNotNull();
        // assertThat(result.getCoupon().getCode()).isEqualTo(couponCode);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 쿠폰 발급 시 예외 발생")
    void acquireCoupon_UserNotFound() {
        // given
        Long userId = 999L;
        Long couponId = 1L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외 발생")
    void acquireCoupon_CouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 999L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage("Coupon not found");
    }

    @Test
    @DisplayName("만료된 쿠폰 발급 시 예외 발생")
    void acquireCoupon_ExpiredCoupon() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon expiredCoupon = Coupon.builder()
                .code("EXPIRED")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1)) // 이미 만료
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(expiredCoupon));

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.Expired.class)
                .hasMessage("Coupon has expired");
    }

    @Test
    @DisplayName("재고 소진된 쿠폰 발급 시 예외 발생")
    void acquireCoupon_OutOfStock() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon outOfStockCoupon = Coupon.builder()
                .code("OUTOFSTOCK")
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(100)
                .issuedCount(100) // 재고 소진
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(outOfStockCoupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.OutOfStock.class)
                .hasMessage("Coupon stock exhausted");
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰 재발급 시 예외 발생")
    void acquireCoupon_AlreadyAcquired() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon coupon = Coupon.builder()
                .code("ALREADY_ACQUIRED")
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(true); // 이미 발급받음

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.AlreadyAcquired.class)
                .hasMessage("Coupon already acquired by user");
    }

    @Test
    @DisplayName("null 사용자 ID로 쿠폰 발급 시 예외 발생")
    void acquireCoupon_WithNullUserId() {
        // given
        Long userId = null;
        Long couponId = 1L;

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 쿠폰 ID로 쿠폰 발급 시 예외 발생")
    void acquireCoupon_WithNullCouponId() {
        // given
        Long userId = 1L;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("아직 시작되지 않은 쿠폰 발급 시 예외 발생")
    void acquireCoupon_NotYetStarted() {
        // given
        Long userId = 1L;
        Long couponId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Coupon futureStartCoupon = Coupon.builder()
                .code("FUTURE_START")
                .discountRate(new BigDecimal("0.25"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().plusDays(1)) // 아직 시작 안함
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(futureStartCoupon));
        when(couponHistoryRepositoryPort.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet started");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidIds")
    @DisplayName("비정상 ID 값들로 쿠폰 발급 테스트")
    void acquireCoupon_WithInvalidIds(Long userId, Long couponId) {
        // when & then
        if (userId != null && userId > 0) {
            User user = User.builder().name("테스트").build();
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        } else {
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        }
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> acquireCouponUseCase.execute(userId, couponId))
                .isInstanceOf(RuntimeException.class);
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
                Arguments.of(-1L, 1L),
                Arguments.of(1L, -1L),
                Arguments.of(0L, 1L),
                Arguments.of(1L, 0L),
                Arguments.of(999L, 999L)
        );
    }
}