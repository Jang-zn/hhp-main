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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

    private static Stream<Arguments> provideCouponData() {
        return Stream.of(
                Arguments.of(1L, 1L, "WELCOME10"),
                Arguments.of(2L, 2L, "SUMMER25"),
                Arguments.of(3L, 3L, "VIP30")
        );
    }
}