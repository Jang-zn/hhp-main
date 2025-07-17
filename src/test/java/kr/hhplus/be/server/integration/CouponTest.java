package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.CommonResponse;
import kr.hhplus.be.server.api.SuccessResponseAdvice;
import kr.hhplus.be.server.api.controller.CouponController;
import kr.hhplus.be.server.api.dto.response.CouponResponse;
import kr.hhplus.be.server.domain.usecase.GetCouponsUseCase;
import kr.hhplus.be.server.domain.usecase.AcquireCouponUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.hamcrest.Matchers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CouponController.class)
@AutoConfigureMockMvc
@Import(SuccessResponseAdvice.class)
@DisplayName("쿠폰 API E2E 테스트")
public class CouponTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcquireCouponUseCase acquireCouponUseCase;

    @MockBean
    private GetCouponsUseCase getCouponsUseCase;

    @Test
    @DisplayName("쿠폰 발급 API 테스트")
    void acquireCouponTest() throws Exception {
        // given
        long userId = 1L;
        long couponId = 1L;
        CouponResponse couponResponse = new CouponResponse(1L, "WELCOME10", new BigDecimal("10"), LocalDateTime.now().plusDays(30));
        given(acquireCouponUseCase.execute(any(), any())).willReturn(couponResponse);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/coupon/acquire")
                        .param("userId", String.valueOf(userId))
                        .param("couponId", String.valueOf(couponId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("WELCOME10"));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API 테스트")
    void getCouponsTest() throws Exception {
        // given
        long userId = 1L;
        List<CouponResponse> couponResponses = List.of(
                new CouponResponse(1L, "WELCOME10", new BigDecimal("10"), LocalDateTime.now().plusDays(30)),
                new CouponResponse(2L, "VIP20", new BigDecimal("20"), LocalDateTime.now().plusDays(7))
        );
        given(getCouponsUseCase.execute(any(), anyInt(), anyInt())).willReturn(couponResponses);

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/coupon/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()", Matchers.is(2)))
                .andExpect(jsonPath("$.data[0].code").value("WELCOME10"))
                .andExpect(jsonPath("$.data[1].code").value("VIP20"));
    }
} 