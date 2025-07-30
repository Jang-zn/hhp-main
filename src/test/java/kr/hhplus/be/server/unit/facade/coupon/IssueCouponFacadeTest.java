package kr.hhplus.be.server.unit.facade.coupon;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("IssueCouponFacade 단위 테스트")
class IssueCouponFacadeTest {

    @Mock
    private IssueCouponUseCase issueCouponUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    private IssueCouponFacade issueCouponFacade;
    
    private Coupon testCoupon;
    private CouponHistory testCouponHistory;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        issueCouponFacade = new IssueCouponFacade(issueCouponUseCase, lockingPort);
        
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

    @Nested
    @DisplayName("쿠폰 발급")
    class IssueCoupon {
        
        @Test
        @DisplayName("성공 - 정상 쿠폰 발급")
        void issueCoupon_Success() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
            when(issueCouponUseCase.execute(userId, couponId)).thenReturn(testCouponHistory);
            
            // when
            CouponHistory result = issueCouponFacade.issueCoupon(userId, couponId);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getCoupon().getId()).isEqualTo(couponId);
            assertThat(result.getStatus()).isEqualTo(CouponHistoryStatus.ISSUED);
            
            verify(lockingPort).acquireLock("coupon-" + couponId);
            verify(issueCouponUseCase).execute(userId, couponId);
            verify(lockingPort).releaseLock("coupon-" + couponId);
        }
        
        @Test
        @DisplayName("실패 - 락 획득 실패")
        void issueCoupon_LockAcquisitionFailed() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> issueCouponFacade.issueCoupon(userId, couponId))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
                
            verify(lockingPort).acquireLock("coupon-" + couponId);
            verify(issueCouponUseCase, never()).execute(any(), any());
            verify(lockingPort, never()).releaseLock(any());
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생 시 락 해제")
        void issueCoupon_UseCaseException_ReleaseLock() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
            when(issueCouponUseCase.execute(userId, couponId))
                .thenThrow(new CouponException.NotFound());
            
            // when & then
            assertThatThrownBy(() -> issueCouponFacade.issueCoupon(userId, couponId))
                .isInstanceOf(CouponException.NotFound.class);
                
            verify(lockingPort).acquireLock("coupon-" + couponId);
            verify(issueCouponUseCase).execute(userId, couponId);
            verify(lockingPort).releaseLock("coupon-" + couponId);
        }
        
        @Test
        @DisplayName("실패 - 이미 발급된 쿠폰")
        void issueCoupon_AlreadyIssued() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
            when(issueCouponUseCase.execute(userId, couponId))
                .thenThrow(new CouponException.AlreadyIssued());
            
            // when & then
            assertThatThrownBy(() -> issueCouponFacade.issueCoupon(userId, couponId))
                .isInstanceOf(CouponException.AlreadyIssued.class);
                
            verify(lockingPort).releaseLock("coupon-" + couponId);
        }
        
        @Test
        @DisplayName("실패 - 쿠폰 발급 한도 초과")
        void issueCoupon_LimitExceeded() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            when(lockingPort.acquireLock("coupon-" + couponId)).thenReturn(true);
            when(issueCouponUseCase.execute(userId, couponId))
                .thenThrow(new CouponException.LimitExceeded());
            
            // when & then
            assertThatThrownBy(() -> issueCouponFacade.issueCoupon(userId, couponId))
                .isInstanceOf(CouponException.LimitExceeded.class);
                
            verify(lockingPort).releaseLock("coupon-" + couponId);
        }
    }
}