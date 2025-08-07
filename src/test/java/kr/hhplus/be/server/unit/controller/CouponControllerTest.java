package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.facade.coupon.GetCouponListFacade;
import kr.hhplus.be.server.domain.facade.coupon.IssueCouponFacade;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * CouponController 비즈니스 시나리오 테스트
 * 
 * Why: 쿠폰 컨트롤러의 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하는지 검증
 * How: 고객의 쿠폰 발급 및 조회 시나리오를 반영한 컨트롤러 레이어 테스트로 구성
 */
@DisplayName("쿠폰 컨트롤러 API 비즈니스 시나리오")
class CouponControllerTest {

    @Mock
    private IssueCouponFacade issueCouponFacade;

    @Mock
    private GetCouponListFacade getCouponListFacade;
    
    @Mock
    private CouponRepositoryPort couponRepositoryPort;
    
    private CouponController couponController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        couponController = new CouponController(issueCouponFacade, getCouponListFacade, couponRepositoryPort);
    }

    @Test
    @DisplayName("고객이 이벤트 쿠폰을 성공적으로 발급받는다")
    void issueCoupon_Success() {
        // given - 고객이 이벤트 페이지에서 쿠폰을 발급받는 상황
        CouponRequest couponRequest = new CouponRequest(1L, 1L);
        CouponHistory issuedHistory = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(1L)
                .userId(1L)
                .couponId(1L)
                .build();
        Coupon activeCoupon = TestBuilder.CouponBuilder.activeCoupon()
                .id(1L)
                .code("이벤트-쿠폰-001")
                .discountRate(new BigDecimal("10.0"))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        
        when(issueCouponFacade.issueCoupon(1L, 1L)).thenReturn(issuedHistory);
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(activeCoupon));
        
        // when
        CouponResponse result = couponController.issueCoupon(couponRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.couponHistoryId()).isEqualTo(1L);
        assertThat(result.couponId()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("이벤트-쿠폰-001");
        assertThat(result.discountRate()).isEqualTo(new BigDecimal("10.0"));
        assertThat(result.couponStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(result.historyStatus()).isEqualTo(CouponHistoryStatus.ISSUED);
        assertThat(result.usable()).isTrue();
        verify(issueCouponFacade).issueCoupon(1L, 1L);
    }
    
    @Test
    @DisplayName("잘못된 요청 형식으로 쿠폰 발급 시 예외가 발생한다")
    void issueCoupon_NullRequest() {
        // given - 잘못된 API 요청
        CouponRequest invalidRequest = null;
        
        // when & then
        assertThatThrownBy(() -> couponController.issueCoupon(invalidRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
        verify(issueCouponFacade, never()).issueCoupon(anyLong(), anyLong());
    }
    
    @Test
    @DisplayName("사용자 ID 누락 시 쿠폰 발급에 실패한다")
    void issueCoupon_MissingUserId() {
        // given - 사용자 ID가 누락된 쿠폰 발급 요청
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
    @DisplayName("쿠폰 ID 누락 시 쿠폰 발급에 실패한다")
    void issueCoupon_MissingCouponId() {
        // given - 쿠폰 ID가 누락된 쿠폰 발급 요청
        CouponRequest request = new CouponRequest();
        request.setUserId(1L);
        // couponId는 null
      
        // when & then
        assertThatThrownBy(() -> couponController.issueCoupon(request))
            .isInstanceOf(CouponException.UserIdAndCouponIdRequired.class);
        verify(issueCouponFacade, never()).issueCoupon(anyLong(), anyLong());
    }
    
    @Test
    @DisplayName("존재하지 않는 쿠폰을 발급 시도할 때 예외가 발생한다")
    void issueCoupon_CouponNotFound() {
        // given - 존재하지 않는 쿠폰에 대한 발급 요청
        CouponRequest request = new CouponRequest(1L, 999L);
        when(issueCouponFacade.issueCoupon(1L, 999L))
            .thenThrow(new CouponException.NotFound());
        
        // when & then
        assertThatThrownBy(() -> couponController.issueCoupon(request))
            .isInstanceOf(CouponException.NotFound.class);
        verify(issueCouponFacade).issueCoupon(1L, 999L);
    }

    @Test
    @DisplayName("고객이 보유한 쿠폰 목록을 성공적으로 조회한다")
    void getCoupons_Success() {
        // given - 고객이 마이페이지에서 보유 쿠폰을 확인하는 상황
        Long customerId = 1L;
        CouponRequest pageRequest = new CouponRequest();
        pageRequest.setLimit(10);
        pageRequest.setOffset(0);
        
        CouponHistory issuedHistory = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(1L)
                .userId(customerId)
                .couponId(1L)
                .build();
        CouponHistory usedHistory = TestBuilder.CouponHistoryBuilder.usedCouponHistory()
                .id(2L)
                .userId(customerId)
                .couponId(1L)
                .usedAt(LocalDateTime.now())
                .build();
        Coupon customerCoupon = TestBuilder.CouponBuilder.activeCoupon()
                .id(1L)
                .code("이벤트-쿠폰")
                .build();
            
        when(getCouponListFacade.getCouponList(customerId, 10, 0))
            .thenReturn(List.of(issuedHistory, usedHistory));
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(customerCoupon));
        
        // when
        List<CouponResponse> result = couponController.getCoupons(customerId, pageRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        
        assertThat(result.get(0).couponHistoryId()).isEqualTo(1L);
        assertThat(result.get(0).historyStatus()).isEqualTo(CouponHistoryStatus.ISSUED);
        assertThat(result.get(0).usable()).isTrue();
        
        assertThat(result.get(1).couponHistoryId()).isEqualTo(2L);
        assertThat(result.get(1).historyStatus()).isEqualTo(CouponHistoryStatus.USED);
        assertThat(result.get(1).usable()).isFalse();
        
        verify(getCouponListFacade).getCouponList(customerId, 10, 0);
    }
    
    @Test
    @DisplayName("쿠폰을 보유하지 않은 신규 고객에게 빈 목록을 반환한다")
    void getCoupons_EmptyList() {
        // given - 아직 쿠폰을 발급받지 않은 신규 고객
        Long newCustomerId = 1L;
        CouponRequest pageRequest = new CouponRequest();
        pageRequest.setLimit(10);
        pageRequest.setOffset(0);
        when(getCouponListFacade.getCouponList(newCustomerId, 10, 0)).thenReturn(List.of());
        
        // when
        List<CouponResponse> result = couponController.getCoupons(newCustomerId, pageRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(0);
        verify(getCouponListFacade).getCouponList(newCustomerId, 10, 0);
    }
    
    @Test
    @DisplayName("잘못된 사용자 ID로 쿠폰 목록 조회 시 예외가 발생한다")
    void getCoupons_NullUserId() {
        // given - 잘못된 사용자 ID로 쿠폰 목록 조회 요청
        Long nullUserId = null;
        CouponRequest pageRequest = new CouponRequest();
        pageRequest.setLimit(10);
        pageRequest.setOffset(0);
        
        // when & then
        assertThatThrownBy(() -> couponController.getCoupons(nullUserId, pageRequest))
            .isInstanceOf(UserException.UserIdCannotBeNull.class);
        verify(getCouponListFacade, never()).getCouponList(anyLong(), anyInt(), anyInt());
    }
    
    @Test
    @DisplayName("잘못된 요청 형식으로 쿠폰 목록 조회 시 예외가 발생한다")
    void getCoupons_NullRequest() {
        // given - 잘못된 API 요청 형식
        Long customerId = 1L;
        CouponRequest invalidRequest = null;
        
        // when & then
        assertThatThrownBy(() -> couponController.getCoupons(customerId, invalidRequest))
            .isInstanceOf(CommonException.InvalidRequest.class);
        verify(getCouponListFacade, never()).getCouponList(anyLong(), anyInt(), anyInt());
    }
    
    @Test
    @DisplayName("쿠폰 목록 조회 중 데이터베이스 오류 시 예외가 전파된다")
    void getCoupons_DatabaseError() {
        // given - 데이터베이스 연결 오류 상황
        Long customerId = 1L;
        CouponRequest pageRequest = new CouponRequest();
        pageRequest.setLimit(10);
        pageRequest.setOffset(0);
        when(getCouponListFacade.getCouponList(customerId, 10, 0))
            .thenThrow(new RuntimeException("Database connection error"));
        
        // when & then
        assertThatThrownBy(() -> couponController.getCoupons(customerId, pageRequest))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database connection error");
        verify(getCouponListFacade).getCouponList(customerId, 10, 0);
    }
    
    @Test
    @DisplayName("일부 쿠폰 조회 실패 시에도 정상 쿠폰들은 반환한다")
    void getCoupons_PartialSuccess_WhenSomeCouponsNotFound() {
        // given - 일부 쿠폰이 삭제되거나 데이터 불일치가 있는 상황
        Long customerId = 1L;
        CouponRequest pageRequest = new CouponRequest();
        pageRequest.setLimit(10);
        pageRequest.setOffset(0);
        
        CouponHistory validHistory = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(1L)
                .userId(customerId)
                .couponId(1L)
                .build();
        CouponHistory invalidHistory = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(2L)
                .userId(customerId)
                .couponId(999L) // 존재하지 않는 쿠폰 ID
                .build();
        CouponHistory anotherValidHistory = TestBuilder.CouponHistoryBuilder.usedCouponHistory()
                .id(3L)
                .userId(customerId)
                .couponId(1L)
                .usedAt(LocalDateTime.now().minusDays(1))
                .build();
        Coupon existingCoupon = TestBuilder.CouponBuilder.activeCoupon()
                .id(1L)
                .build();
            
        when(getCouponListFacade.getCouponList(customerId, 10, 0))
            .thenReturn(List.of(validHistory, invalidHistory, anotherValidHistory));
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(existingCoupon));
        when(couponRepositoryPort.findById(999L)).thenReturn(Optional.empty()); // 존재하지 않는 쿠폰
        
        // when
        List<CouponResponse> result = couponController.getCoupons(customerId, pageRequest);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2); // 3개 중 2개만 성공적으로 반환
        
        assertThat(result.get(0).couponHistoryId()).isEqualTo(1L);
        assertThat(result.get(0).historyStatus()).isEqualTo(CouponHistoryStatus.ISSUED);
        assertThat(result.get(0).usable()).isTrue();
        
        assertThat(result.get(1).couponHistoryId()).isEqualTo(3L);
        assertThat(result.get(1).historyStatus()).isEqualTo(CouponHistoryStatus.USED);
        assertThat(result.get(1).usable()).isFalse();
        
        verify(getCouponListFacade).getCouponList(customerId, 10, 0);
        verify(couponRepositoryPort, times(2)).findById(1L);
        verify(couponRepositoryPort).findById(999L);
    }
}