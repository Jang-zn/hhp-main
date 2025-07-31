package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
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
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    private ApplyCouponUseCase applyCouponUseCase;
    
    private Coupon testCoupon;
    private CouponHistory testCouponHistory;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        applyCouponUseCase = new ApplyCouponUseCase(couponRepositoryPort, couponHistoryRepositoryPort);
        
        testCoupon = Coupon.builder()
            .id(1L)
            .code("COUPON123")
            .discountRate(new BigDecimal("0.1"))
            .endDate(LocalDateTime.now().plusDays(30))
            .status(CouponStatus.ACTIVE)
            .build();
            
        testCouponHistory = CouponHistory.builder()
            .id(1L)
            .coupon(testCoupon)
            .issuedAt(LocalDateTime.now())
            .status(CouponHistoryStatus.ISSUED)
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
        when(couponHistoryRepositoryPort.findByCouponId(couponId)).thenReturn(Optional.of(testCouponHistory));
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        BigDecimal result = applyCouponUseCase.execute(couponId, originalAmount);
        
        // then
        assertThat(result).isEqualTo(expectedDiscountedAmount);
        assertThat(testCouponHistory.getStatus()).isEqualTo(CouponHistoryStatus.USED);
        
        verify(couponHistoryRepositoryPort).save(testCouponHistory);
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 쿠폰")
    void execute_CouponNotFound() {
        // given
        Long couponId = 999L;
        BigDecimal originalAmount = new BigDecimal("100000");
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> applyCouponUseCase.execute(couponId, originalAmount))
            .isInstanceOf(CouponException.NotFound.class);
            
        verify(couponHistoryRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 쿠폰 히스토리가 없음 (발급되지 않은 쿠폰)")
    void execute_CouponHistoryNotFound() {
        // given
        Long couponId = 1L;
        BigDecimal originalAmount = new BigDecimal("100000");
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponHistoryRepositoryPort.findByCouponId(couponId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> applyCouponUseCase.execute(couponId, originalAmount))
            .isInstanceOf(CouponException.NotIssued.class);
            
        verify(couponHistoryRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 이미 사용된 쿠폰")
    void execute_AlreadyUsedCoupon() {
        // given
        Long couponId = 1L;
        BigDecimal originalAmount = new BigDecimal("100000");
        
        CouponHistory usedCouponHistory = CouponHistory.builder()
            .id(1L)
            .coupon(testCoupon)
            .issuedAt(LocalDateTime.now())
            .status(CouponHistoryStatus.USED)
            .build();
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(couponHistoryRepositoryPort.findByCouponId(couponId)).thenReturn(Optional.of(usedCouponHistory));
        
        // when & then
        assertThatThrownBy(() -> applyCouponUseCase.execute(couponId, originalAmount))
            .isInstanceOf(CouponException.AlreadyUsed.class);
            
        verify(couponHistoryRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 만료된 쿠폰")
    void execute_ExpiredCoupon() {
        // given
        Long couponId = 1L;
        BigDecimal originalAmount = new BigDecimal("100000");
        
        Coupon expiredCoupon = Coupon.builder()
            .id(1L)
            .code("EXPIRED123")
            .discountRate(new BigDecimal("0.1"))
            .endDate(LocalDateTime.now().minusDays(1))
            .status(CouponStatus.ACTIVE)
            .build();
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(expiredCoupon));
        when(couponHistoryRepositoryPort.findByCouponId(couponId)).thenReturn(Optional.of(testCouponHistory));
        
        // when & then
        assertThatThrownBy(() -> applyCouponUseCase.execute(couponId, originalAmount))
            .isInstanceOf(CouponException.Expired.class);
            
        verify(couponHistoryRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 비활성화된 쿠폰")
    void execute_InactiveCoupon() {
        // given
        Long couponId = 1L;
        BigDecimal originalAmount = new BigDecimal("100000");
        
        Coupon inactiveCoupon = Coupon.builder()
            .id(1L)
            .code("INACTIVE123")
            .discountRate(new BigDecimal("0.1"))
            .endDate(LocalDateTime.now().plusDays(30))
            .status(CouponStatus.INACTIVE)
            .build();
        
        when(couponRepositoryPort.findById(couponId)).thenReturn(Optional.of(inactiveCoupon));
        when(couponHistoryRepositoryPort.findByCouponId(couponId)).thenReturn(Optional.of(testCouponHistory));
        
        // when & then
        assertThatThrownBy(() -> applyCouponUseCase.execute(couponId, originalAmount))
            .isInstanceOf(CouponException.Inactive.class);
            
        verify(couponHistoryRepositoryPort, never()).save(any());
    }
}