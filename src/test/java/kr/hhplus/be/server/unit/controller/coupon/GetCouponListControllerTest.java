package kr.hhplus.be.server.unit.controller.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.service.CouponService;
import kr.hhplus.be.server.api.controller.CouponController;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponController.getCouponList 메서드 테스트
 * 
 * Why: 쿠폰 목록 조회 API 엔드포인트가 비즈니스 요구사항을 올바르게 처리하고 Bean Validation이 작동하는지 검증
 * How: MockMvc를 사용한 컨트롤러 테스트로 HTTP 요청/응답 검증
 */
@WebMvcTest(CouponController.class)
@ActiveProfiles("unit")
@DisplayName("쿠폰 목록 조회 컨트롤러 API")
class GetCouponListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;
    

    @Test
    @DisplayName("고객이 자신의 쿠폰 목록을 성공적으로 조회한다")
    void getCouponList_Success() throws Exception {
        // given - 고객이 마이페이지에서 쿠폰 목록을 확인하는 상황
        Long customerId = 1L;
        int limit = 10;
        int offset = 0;

        List<CouponHistory> couponHistories = List.of(
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .id(1L)
                        .userId(customerId)
                        .couponId(1L)
                        .status(CouponHistoryStatus.ISSUED)
                        .build(),
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .id(2L)
                        .userId(customerId)
                        .couponId(2L)
                        .status(CouponHistoryStatus.USED)
                        .build()
        );

        when(couponService.getCouponList(customerId, limit, offset)).thenReturn(couponHistories);
        
        // Mock getCouponById calls for each coupon
        Coupon coupon1 = TestBuilder.CouponBuilder.defaultCoupon()
                .id(1L)
                .code("COUPON1")
                .discountRate(new BigDecimal("0.10"))
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon coupon2 = TestBuilder.CouponBuilder.defaultCoupon()
                .id(2L)
                .code("COUPON2")
                .discountRate(new BigDecimal("0.20"))
                .status(CouponStatus.ACTIVE)
                .build();
                
        when(couponService.getCouponById(1L)).thenReturn(coupon1);
        when(couponService.getCouponById(2L)).thenReturn(coupon2);

        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(customerId))
                .andExpect(jsonPath("$.data[0].couponId").value(1L))
                .andExpect(jsonPath("$.data[1].userId").value(customerId))
                .andExpect(jsonPath("$.data[1].couponId").value(2L));
    }

    @Test
    @DisplayName("빈 쿠폰 목록 조회가 성공한다")
    void getCouponList_EmptyList() throws Exception {
        // given
        Long customerId = 1L;
        int limit = 10;
        int offset = 0;

        when(couponService.getCouponList(customerId, limit, offset)).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L, "음수 사용자 ID"),
                Arguments.of(0L, "0인 사용자 ID")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("유효하지 않은 사용자 ID로 쿠폰 목록 조회 시 validation 에러가 발생한다")
    void getCouponList_InvalidUserId_ValidationError(Long invalidUserId, String description) throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", invalidUserId)
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                Arguments.of(-1, 0, "음수 limit"),
                Arguments.of(0, 0, "0인 limit"),
                Arguments.of(101, 0, "최대값 초과 limit"),
                Arguments.of(10, -1, "음수 offset")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("유효하지 않은 페이징 파라미터로 조회 시 validation 에러가 발생한다")
    void getCouponList_InvalidPaginationParams_ValidationError(int limit, int offset, String description) throws Exception {
        // given
        Long customerId = 1L;

        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", customerId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V001"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 입력입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 쿠폰 목록 조회 시 예외가 발생한다")
    void getCouponList_UserNotFound() throws Exception {
        // given
        Long invalidUserId = 999L;
        int limit = 10;
        int offset = 0;

        when(couponService.getCouponList(invalidUserId, limit, offset))
                .thenThrow(new UserException.NotFound());

        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", invalidUserId)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("문자열 사용자 ID로 쿠폰 목록 조회 시 에러가 발생한다")
    void getCouponList_StringUserId_Error() throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupon/user/{userId}", "invalid")
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().is4xxClientError());
    }
}