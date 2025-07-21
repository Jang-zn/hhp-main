package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.SuccessResponseAdvice;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Autowired
    private UserRepositoryPort userRepositoryPort;
    @Autowired
    private CouponRepositoryPort couponRepositoryPort;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 설정
        User user = User.builder().id(1L).name("Test User").build();
        userRepositoryPort.save(user);

        // 테스트 쿠폰 설정
        Coupon coupon = Coupon.builder()
                .id(1L)
                .code("TEST_COUPON")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        couponRepositoryPort.save(coupon);
    }

    @Test
    @DisplayName("쿠폰 발급 API 테스트")
    void issueCouponTest() throws Exception {
        // given
        long userId = 1L;
        long couponId = 1L;

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/coupon/issue")
                        .param("userId", String.valueOf(userId))
                        .param("couponId", String.valueOf(couponId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponId").value(couponId));
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
} 