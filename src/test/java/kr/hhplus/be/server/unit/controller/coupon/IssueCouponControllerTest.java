package kr.hhplus.be.server.unit.controller.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.service.CouponService;
import kr.hhplus.be.server.util.ControllerTestBase;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponController.issueCoupon 메서드 테스트
 * 
 * Why: 쿠폰 발급 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 통합 테스트로 HTTP 요청/응답 전체 플로우 검증
 */
@Transactional
@DisplayName("쿠폰 발급 컨트롤러 API")
class IssueCouponControllerTest extends ControllerTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CouponService couponService;


    @Test
    @DisplayName("고객이 쿠폰을 성공적으로 발급받는다")
    void issueCoupon_Success() throws Exception {
        // given - 고객이 이벤트 페이지에서 쿠폰을 발급받는 상황
        Long customerId = 1L;
        Long couponId = 1L;
        CouponRequest request = new CouponRequest(customerId, couponId);

        CouponHistory issuedCoupon = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .id(1L)
                .userId(customerId)
                .couponId(couponId)
                .status(CouponHistoryStatus.ISSUED)
                .build();

        when(couponService.issueCoupon(couponId, customerId)).thenReturn(issuedCoupon);
        
        // Mock the coupon entity and getCouponById call
        Coupon mockCoupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(couponId)
                .code("DISCOUNT_10")
                .discountRate(new BigDecimal("0.10"))
                .status(CouponStatus.ACTIVE)
                .build();
        
        when(couponService.getCouponById(couponId)).thenReturn(mockCoupon);

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data.userId").value(customerId))
                .andExpect(jsonPath("$.data.couponId").value(couponId));
    }

    static Stream<Arguments> provideInvalidCouponRequests() {
        return Stream.of(
                // userId가 null
                Arguments.of(null, 1L, "사용자 ID가 null"),
                // couponId가 null
                Arguments.of(1L, null, "쿠폰 ID가 null"),
                // userId가 음수
                Arguments.of(-1L, 1L, "사용자 ID가 음수"),
                // userId가 0
                Arguments.of(0L, 1L, "사용자 ID가 0"),
                // couponId가 음수
                Arguments.of(1L, -1L, "쿠폰 ID가 음수"),
                // couponId가 0
                Arguments.of(1L, 0L, "쿠폰 ID가 0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidCouponRequests")
    @DisplayName("유효하지 않은 쿠폰 발급 요청 시 Bean Validation 에러가 발생한다")
    void issueCoupon_InvalidRequest_ValidationError(Long userId, Long couponId, String description) throws Exception {
        // given
        CouponRequest request = new CouponRequest(userId, couponId);

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("빈 요청 본문으로 쿠폰 발급 시 validation 에러가 발생한다")
    void issueCoupon_EmptyBody_ValidationError() throws Exception {
        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 시 예외가 발생한다")
    void issueCoupon_CouponNotFound() throws Exception {
        // given
        Long customerId = 1L;
        Long invalidCouponId = 999L;
        CouponRequest request = new CouponRequest(customerId, invalidCouponId);

        when(couponService.issueCoupon(invalidCouponId, customerId))
                .thenThrow(new CouponException.NotFound());

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰을 재발급 시 예외가 발생한다")
    void issueCoupon_AlreadyIssued() throws Exception {
        // given
        Long customerId = 1L;
        Long couponId = 1L;
        CouponRequest request = new CouponRequest(customerId, couponId);

        when(couponService.issueCoupon(couponId, customerId))
                .thenThrow(new CouponException.AlreadyIssued());

        // when & then
        mockMvc.perform(post("/api/coupon/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C005"));
    }
}