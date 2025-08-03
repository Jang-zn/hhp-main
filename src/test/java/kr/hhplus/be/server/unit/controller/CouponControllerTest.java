package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.TestConstants;
import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.facade.coupon.GetCouponListFacade;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CouponController 단위 테스트")
class CouponControllerTest {

    @Mock
    private IssueCouponFacade issueCouponFacade;

    @Mock
    private GetCouponListFacade getCouponListFacade;
    
    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    private CouponController couponController;
    
    private User testUser;
    private Coupon testCoupon;
    private CouponHistory testCouponHistory;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        couponController = new CouponController(issueCouponFacade, getCouponListFacade, couponRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name(TestConstants.TEST_USER_NAME)
            .build();
            
        testCoupon = Coupon.builder()
            .id(1L)
            .code(TestConstants.TEST_COUPON_CODE)
            .discountRate(TestConstants.DEFAULT_DISCOUNT_RATE)
            .endDate(LocalDateTime.now().plusDays(30))
            .status(CouponStatus.ACTIVE)
            .build();
            
        testCouponHistory = CouponHistory.builder()
            .id(1L)
            .userId(testUser.getId())
            .couponId(testCoupon.getId())
            .status(CouponHistoryStatus.ISSUED)
            .issuedAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("쿠폰 발급")
    class IssueCoupon {
        
        @Test
        @DisplayName("성공 - 정상 쿠폰 발급")
        void issueCoupon_Success() {
            // given
            CouponRequest request = new CouponRequest(1L, 1L);
            when(issueCouponFacade.issueCoupon(1L, 1L)).thenReturn(testCouponHistory);
            when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(testCoupon));
            
            // when
            CouponResponse result = couponController.issueCoupon(request);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.couponHistoryId()).isEqualTo(1L);
            assertThat(result.couponId()).isEqualTo(1L);
            assertThat(result.code()).isEqualTo("TEST-COUPON-001");  // testCoupon의 실제 코드
            assertThat(result.discountRate()).isEqualTo(new BigDecimal("10.0"));
            assertThat(result.couponStatus()).isEqualTo(CouponStatus.ACTIVE);
            assertThat(result.historyStatus()).isEqualTo(CouponHistoryStatus.ISSUED);
            assertThat(result.usable()).isTrue();
            
            verify(issueCouponFacade).issueCoupon(1L, 1L);
        }
        
        @Test
        @DisplayName("실패 - null 요청")
        void issueCoupon_NullRequest() {
            // given
            CouponRequest nullRequest = null;
            
            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(nullRequest))
                .isInstanceOf(CommonException.InvalidRequest.class);
                
            verify(issueCouponFacade, never()).issueCoupon(anyLong(), anyLong());
        }
        
        @Test
        @DisplayName("실패 - 사용자 ID 누락")
        void issueCoupon_MissingUserId() {
            // given
            CouponRequest request = new CouponRequest();
            request.setCouponId(1L);
            // userId는 null
            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 사용자 ID입니다.");
                
            verify(issueCouponFacade, never()).issueCoupon(anyLong(), anyLong());
        }
        
        @Test
        @DisplayName("실패 - 쿠폰 ID 누락")
        void issueCoupon_MissingCouponId() {
            // given
            CouponRequest request = new CouponRequest();
            request.setUserId(1L);
            // couponId는 null
          
            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(request))
                .isInstanceOf(CouponException.UserIdAndCouponIdRequired.class);
                
            verify(issueCouponFacade, never()).issueCoupon(anyLong(), anyLong());
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생")
        void issueCoupon_UseCaseException() {
            // given
            CouponRequest request = new CouponRequest(1L, 1L);
            when(issueCouponFacade.issueCoupon(1L, 1L))
                .thenThrow(new CouponException.NotFound());
            
            // when & then
            assertThatThrownBy(() -> couponController.issueCoupon(request))
                .isInstanceOf(CouponException.NotFound.class);
                
            verify(issueCouponFacade).issueCoupon(1L, 1L);
        }
    }

    @Nested
    @DisplayName("보유 쿠폰 조회")
    class GetCoupons {
        
        @Test
        @DisplayName("성공 - 정상 쿠폰 목록 조회")
        void getCoupons_Success() {
            // given
            Long userId = 1L;
            CouponRequest request = new CouponRequest();
            request.setLimit(10);
            request.setOffset(0);
            
            CouponHistory history2 = CouponHistory.builder()
                .id(2L)
                .userId(testUser.getId())
                .couponId(testCoupon.getId())
                .status(CouponHistoryStatus.USED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .usedAt(LocalDateTime.now())
                .build();
                
            when(getCouponListFacade.getCouponList(userId, 10, 0))
                .thenReturn(List.of(testCouponHistory, history2));
            when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(testCoupon));
            when(couponRepositoryPort.findById(2L)).thenReturn(Optional.of(testCoupon));
            
            // when
            List<CouponResponse> result = couponController.getCoupons(userId, request);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            
            assertThat(result.get(0).couponHistoryId()).isEqualTo(1L);
            assertThat(result.get(0).historyStatus()).isEqualTo(CouponHistoryStatus.ISSUED);
            assertThat(result.get(0).usable()).isTrue();
            
            assertThat(result.get(1).couponHistoryId()).isEqualTo(2L);
            assertThat(result.get(1).historyStatus()).isEqualTo(CouponHistoryStatus.USED);
            assertThat(result.get(1).usable()).isFalse();
            
            verify(getCouponListFacade).getCouponList(userId, 10, 0);
        }
        
        @Test
        @DisplayName("성공 - 빈 쿠폰 목록")
        void getCoupons_EmptyList() {
            // given
            Long userId = 1L;
            CouponRequest request = new CouponRequest();
            request.setLimit(10);
            request.setOffset(0);
            when(getCouponListFacade.getCouponList(userId, 10, 0)).thenReturn(List.of());
            
            // when
            List<CouponResponse> result = couponController.getCoupons(userId, request);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(0);
            
            verify(getCouponListFacade).getCouponList(userId, 10, 0);
        }
        
        @Test
        @DisplayName("실패 - null 사용자 ID")
        void getCoupons_NullUserId() {
            // given
            Long nullUserId = null;
            CouponRequest request = new CouponRequest();
            request.setLimit(10);
            request.setOffset(0);
            
            // when & then
            assertThatThrownBy(() -> couponController.getCoupons(nullUserId, request))
                .isInstanceOf(UserException.UserIdCannotBeNull.class);
                
            verify(getCouponListFacade, never()).getCouponList(anyLong(), anyInt(), anyInt());
        }
        
        @Test
        @DisplayName("실패 - null 요청")
        void getCoupons_NullRequest() {
            // given
            Long userId = 1L;
            CouponRequest nullRequest = null;
            
            // when & then
            assertThatThrownBy(() -> couponController.getCoupons(userId, nullRequest))
                .isInstanceOf(CommonException.InvalidRequest.class);
                
            verify(getCouponListFacade, never()).getCouponList(anyLong(), anyInt(), anyInt());
        }
        
        @Test
        @DisplayName("실패 - UseCase 실행 중 예외 발생")
        void getCoupons_UseCaseException() {
            // given
            Long userId = 1L;
            CouponRequest request = new CouponRequest();
            request.setLimit(10);
            request.setOffset(0);
            when(getCouponListFacade.getCouponList(userId, 10, 0))
                .thenThrow(new RuntimeException("Database connection error"));
            
            // when & then
            assertThatThrownBy(() -> couponController.getCoupons(userId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection error");
                
            verify(getCouponListFacade).getCouponList(userId, 10, 0);
        }
    }
}