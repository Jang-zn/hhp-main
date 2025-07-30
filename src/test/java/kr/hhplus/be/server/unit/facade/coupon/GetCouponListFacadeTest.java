package kr.hhplus.be.server.unit.facade.coupon;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.coupon.GetCouponListFacade;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetCouponListFacade 단위 테스트")
class GetCouponListFacadeTest {

    @Mock
    private GetCouponListUseCase getCouponListUseCase;
    
    private GetCouponListFacade getCouponListFacade;
    
    private List<CouponHistory> testCouponHistories;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getCouponListFacade = new GetCouponListFacade(getCouponListUseCase);
        
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
            .coupon(coupon1)
            .issuedAt(LocalDateTime.now())
            .status(CouponHistoryStatus.ISSUED)
            .build();
            
        CouponHistory history2 = CouponHistory.builder()
            .id(2L)
            .coupon(coupon2)
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
            
            when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(testCouponHistories);
            
            // when
            List<CouponHistory> result = getCouponListFacade.getCouponList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCoupon().getCode()).isEqualTo("COUPON123");
            assertThat(result.get(1).getCoupon().getCode()).isEqualTo("COUPON456");
            
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
        
        @Test
        @DisplayName("성공 - 빈 쿠폰 목록")
        void getCouponList_EmptyList() {
            // given
            Long userId = 1L;
            int limit = 10;
            int offset = 0;
            
            when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(List.of());
            
            // when
            List<CouponHistory> result = getCouponListFacade.getCouponList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
        
        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void getCouponList_UserNotFound() {
            // given
            Long userId = 999L;
            int limit = 10;
            int offset = 0;
            
            when(getCouponListUseCase.execute(userId, limit, offset))
                .thenThrow(new UserException.NotFound());
            
            // when & then
            assertThatThrownBy(() -> getCouponListFacade.getCouponList(userId, limit, offset))
                .isInstanceOf(UserException.NotFound.class);
                
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
        
        @Test
        @DisplayName("성공 - 페이징 처리")
        void getCouponList_WithPaging() {
            // given
            Long userId = 1L;
            int limit = 5;
            int offset = 10;
            
            when(getCouponListUseCase.execute(userId, limit, offset)).thenReturn(List.of(testCouponHistories.get(0)));
            
            // when
            List<CouponHistory> result = getCouponListFacade.getCouponList(userId, limit, offset);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            
            verify(getCouponListUseCase).execute(userId, limit, offset);
        }
    }
}