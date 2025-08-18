package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.usecase.coupon.ExpireCouponsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ExpireCouponsUseCase 단위 테스트")
class ExpireCouponsUseCaseTest {

    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;

    private ExpireCouponsUseCase expireCouponsUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        expireCouponsUseCase = new ExpireCouponsUseCase(couponRepositoryPort, couponHistoryRepositoryPort);
    }

    @Test
    @DisplayName("만료된 쿠폰들의 상태 업데이트 성공")
    void expireCoupons_Success() {
        // given
        List<Coupon> expiredCoupons = List.of(
                createExpiredCoupon(1L, "EXPIRED1", CouponStatus.ACTIVE),
                createExpiredCoupon(2L, "EXPIRED2", CouponStatus.SOLD_OUT)
        );
        
        List<CouponHistory> expiredHistories = List.of(
                createExpiredCouponHistory(1L, "EXPIRED1"),
                createExpiredCouponHistory(2L, "EXPIRED2")
        );
        
        when(couponRepositoryPort.findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED))))
                .thenReturn(expiredCoupons);
        when(couponHistoryRepositoryPort.findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED)))
                .thenReturn(expiredHistories);
        when(couponRepositoryPort.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        expireCouponsUseCase.execute();

        // then
        verify(couponRepositoryPort).findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED)));
        verify(couponHistoryRepositoryPort).findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED));
        verify(couponRepositoryPort, times(2)).save(any(Coupon.class));
        verify(couponHistoryRepositoryPort, times(2)).save(any(CouponHistory.class));
    }

    @Test
    @DisplayName("만료될 쿠폰이 없는 경우")
    void expireCoupons_NoCouponsToExpire() {
        // given
        when(couponRepositoryPort.findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED))))
                .thenReturn(List.of());
        when(couponHistoryRepositoryPort.findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED)))
                .thenReturn(List.of());

        // when
        expireCouponsUseCase.execute();

        // then
        verify(couponRepositoryPort).findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED)));
        verify(couponHistoryRepositoryPort).findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED));
        verify(couponRepositoryPort, never()).save(any(Coupon.class));
        verify(couponHistoryRepositoryPort, never()).save(any(CouponHistory.class));
    }

    @Test
    @DisplayName("쿠폰만 만료되고 히스토리는 없는 경우")
    void expireCoupons_OnlyCouponsExpire() {
        // given
        List<Coupon> expiredCoupons = List.of(
                createExpiredCoupon(1L, "EXPIRED1", CouponStatus.ACTIVE)
        );
        
        when(couponRepositoryPort.findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED))))
                .thenReturn(expiredCoupons);
        when(couponHistoryRepositoryPort.findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED)))
                .thenReturn(List.of());
        when(couponRepositoryPort.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        expireCouponsUseCase.execute();

        // then
        verify(couponRepositoryPort, times(1)).save(any(Coupon.class));
        verify(couponHistoryRepositoryPort, never()).save(any(CouponHistory.class));
    }

    @Test
    @DisplayName("히스토리만 만료되고 쿠폰은 없는 경우")
    void expireCoupons_OnlyHistoriesExpire() {
        // given
        List<CouponHistory> expiredHistories = List.of(
                createExpiredCouponHistory(1L, "EXPIRED1")
        );
        
        when(couponRepositoryPort.findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED))))
                .thenReturn(List.of());
        when(couponHistoryRepositoryPort.findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED)))
                .thenReturn(expiredHistories);
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        expireCouponsUseCase.execute();

        // then
        verify(couponRepositoryPort, never()).save(any(Coupon.class));
        verify(couponHistoryRepositoryPort, times(1)).save(any(CouponHistory.class));
    }

    @Test
    @DisplayName("쿠폰 상태 업데이트 중 예외 발생해도 다른 쿠폰은 계속 처리")
    void expireCoupons_PartialFailure() {
        // given
        Coupon successCoupon = createExpiredCoupon(1L, "SUCCESS", CouponStatus.ACTIVE);
        Coupon failCoupon = createExpiredCoupon(2L, "FAIL", CouponStatus.ACTIVE);
        
        List<Coupon> expiredCoupons = List.of(successCoupon, failCoupon);
        
        when(couponRepositoryPort.findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED))))
                .thenReturn(expiredCoupons);
        when(couponHistoryRepositoryPort.findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED)))
                .thenReturn(List.of());
        
        when(couponRepositoryPort.save(successCoupon)).thenReturn(successCoupon);
        when(couponRepositoryPort.save(failCoupon)).thenThrow(new RuntimeException("Save failed"));

        // when
        expireCouponsUseCase.execute();

        // then
        verify(couponRepositoryPort, times(2)).save(any(Coupon.class));
    }

    @Test
    @DisplayName("쿠폰 히스토리 상태 업데이트 중 예외 발생해도 다른 히스토리는 계속 처리")
    void expireCoupons_HistoryPartialFailure() {
        // given
        CouponHistory successHistory = createExpiredCouponHistory(1L, "SUCCESS");
        CouponHistory failHistory = createExpiredCouponHistory(2L, "FAIL");
        
        List<CouponHistory> expiredHistories = List.of(successHistory, failHistory);
        
        when(couponRepositoryPort.findExpiredCouponsNotInStatus(any(LocalDateTime.class), 
                eq(List.of(CouponStatus.EXPIRED, CouponStatus.DISABLED))))
                .thenReturn(List.of());
        when(couponHistoryRepositoryPort.findExpiredHistoriesInStatus(any(LocalDateTime.class), eq(CouponHistoryStatus.ISSUED)))
                .thenReturn(expiredHistories);
        
        when(couponHistoryRepositoryPort.save(successHistory)).thenReturn(successHistory);
        when(couponHistoryRepositoryPort.save(failHistory)).thenThrow(new RuntimeException("Save failed"));

        // when
        expireCouponsUseCase.execute();

        // then
        verify(couponHistoryRepositoryPort, times(2)).save(any(CouponHistory.class));
    }

    private Coupon createExpiredCoupon(Long id, String code, CouponStatus status) {
        return Coupon.builder()
                .id(id)
                .code(code)
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1)) // 이미 만료됨
                .status(status)
                .build();
    }

    private CouponHistory createExpiredCouponHistory(Long id, String couponCode) {
        User user = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
        
        Coupon expiredCoupon = Coupon.builder()
                .id(id)
                .code(couponCode)
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1)) // 이미 만료됨
                .status(CouponStatus.EXPIRED)
                .build();

        return CouponHistory.builder()
                .id(id)
                .userId(user.getId())
                .couponId(expiredCoupon.getId())
                .issuedAt(LocalDateTime.now().minusDays(5))
                .status(CouponHistoryStatus.ISSUED)
                .build();
    }
}