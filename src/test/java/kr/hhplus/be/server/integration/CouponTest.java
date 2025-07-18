package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.SuccessResponseAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.hamcrest.Matchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SuccessResponseAdvice.class)
@DisplayName("쿠폰 API E2E 테스트")
public class CouponTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("쿠폰 발급 API 테스트")
    void acquireCouponTest() throws Exception {
        // given
        long userId = 1L;
        long couponId = 1L;

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/coupon/acquire")
                        .param("userId", String.valueOf(userId))
                        .param("couponId", String.valueOf(couponId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API 테스트")
    void getCouponsTest() throws Exception {
        // given
        long userId = 1L;

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/coupon/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk());
    }
} 