package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.usecase.coupon.ApplyCouponUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ApplyCouponUseCase 단위 테스트")
class ApplyCouponUseCaseTest {

    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    private ApplyCouponUseCase applyCouponUseCase;
    
    private Coupon testCoupon;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        applyCouponUseCase = new ApplyCouponUseCase(couponRepositoryPort, cachePort, keyGenerator);
        
        testCoupon = Coupon.builder()
            .id(1L)
            .code("COUPON123")
            .discountRate(new BigDecimal("0.1"))
            .endDate(LocalDateTime.now().plusDays(30))
            .status(CouponStatus.ACTIVE)
            .build();
    }

    @Test
    @DisplayName("성공 - 쿠폰 적용")
    void execute_Success() {
        // given
        Long couponId = 1L;
        BigDecimal originalAmount = new BigDecimal("100000");
        BigDecimal expectedDiscountedAmount = new BigDecimal("90000");
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(testCoupon));
        
        // when
        BigDecimal result = applyCouponUseCase.execute(originalAmount, couponId);
        
        // then
        assertThat(result.compareTo(expectedDiscountedAmount)).isEqualTo(0);
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 쿠폰")
    void execute_CouponNotFound() {
        // given
        Long couponId = 999L;
        BigDecimal originalAmount = new BigDecimal("100000");
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> applyCouponUseCase.execute(originalAmount, couponId))
            .isInstanceOf(CouponException.NotFound.class);
            
        
    }
}