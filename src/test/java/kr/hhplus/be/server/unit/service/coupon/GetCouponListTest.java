package kr.hhplus.be.server.unit.service.coupon;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.service.CouponService;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.service.KeyGenerator;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponService.getCouponList 메서드 테스트
 */
@DisplayName("쿠폰 목록 조회 서비스")
class GetCouponListTest {

    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private GetCouponListUseCase getCouponListUseCase;
    
    @Mock
    private IssueCouponUseCase issueCouponUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;
    
    private CouponService couponService;
    
    private List<CouponHistory> testCouponHistories;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        couponService = new CouponService(transactionTemplate, getCouponListUseCase, issueCouponUseCase, lockingPort, userRepositoryPort, cachePort, keyGenerator);
        
        Coupon coupon1 = Coupon.builder()
            .id(1L)
            .code("COUPON123")
            .discountRate(new BigDecimal("0.1"))
            .endDate(LocalDateTime.now().plusDays(30))
            .status(CouponStatus.ACTIVE)
            .build();
            
        Coupon coupon2 = Coupon.builder()
            .id(2L)
            .code("COUPON456")
            .discountRate(new BigDecimal("0.2"))
            .endDate(LocalDateTime.now().plusDays(15))
            .status(CouponStatus.ACTIVE)
            .build();
            
        CouponHistory history1 = CouponHistory.builder()
            .id(1L)
            .couponId(coupon1.getId())
            .userId(1L)
            .issuedAt(LocalDateTime.now())
            .status(CouponHistoryStatus.ISSUED)
            .build();
            
        CouponHistory history2 = CouponHistory.builder()
            .id(2L)
            .couponId(coupon2.getId())
            .userId(1L)
            .issuedAt(LocalDateTime.now())
            .status(CouponHistoryStatus.ISSUED)
            .build();
            
        testCouponHistories = List.of(history1, history2);
    }

    @Nested
    @DisplayName("쿠폰 목록 조회")
    class GetCouponList {
        
        @Test
        @DisplayName("성공 - 정상 쿠폰 목록 조회")
        void getCouponList_Success() {
            // given
            Long userId = 1L;
            int limit = 10;
            int offset = 0;
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(testCouponHistories);
            
            // when
            List<CouponHistory> result = couponService.getCouponList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCouponId()).isEqualTo(1L);
            assertThat(result.get(1).getCouponId()).isEqualTo(2L);
            
            verify(userRepositoryPort).existsById(userId);
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
        
        @Test
        @DisplayName("성공 - 빈 쿠폰 목록")
        void getCouponList_EmptyList() {
            // given
            Long userId = 1L;
            int limit = 10;
            int offset = 0;
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(List.of());
            
            // when
            List<CouponHistory> result = couponService.getCouponList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            
            verify(userRepositoryPort).existsById(userId);
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void getCouponList_UserNotFound() {
            // given
            Long userId = 999L;
            int limit = 10;
            int offset = 0;
            
            when(userRepositoryPort.existsById(userId)).thenReturn(false);
            
            // when & then
            assertThatThrownBy(() -> couponService.getCouponList(userId, limit, offset))
                .isInstanceOf(UserException.NotFound.class);
                
            verify(userRepositoryPort).existsById(userId);
            verify(getCouponListUseCase, never()).execute(any(), anyInt(), anyInt());
        }
        
        @Test
        @DisplayName("성공 - 페이징 처리")
        void getCouponList_WithPaging() {
            // given
            Long userId = 1L;
            int limit = 5;
            int offset = 10;
            
            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(List.of(testCouponHistories.get(0)));
            
            // when
            List<CouponHistory> result = couponService.getCouponList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            
            verify(userRepositoryPort).existsById(userId);
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
    }
}