package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.api.controller.BalanceController;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("잔액 API E2E 테스트")
public class BalanceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepositoryPort userRepositoryPort;
    @Autowired
    private BalanceRepositoryPort balanceRepositoryPort;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 및 초기 잔액 설정
        User user = User.builder().id(1L).name("Test User").build();
        userRepositoryPort.save(user);

        Balance balance = Balance.builder()
                                .user(user)
                                .amount(new BigDecimal("50000"))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
        balanceRepositoryPort.save(balance);
    }

    @Test
    @DisplayName("잔액 충전 API 테스트")
    void chargeBalanceTest() throws Exception {
        // given
        long userId = 1L;
        BigDecimal amount = new BigDecimal("10000");
        BalanceRequest request = new BalanceRequest(userId, amount);

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.amount").value(new BigDecimal("60000"))); // 50000 + 10000
    }

    @Test
    @DisplayName("잔액 조회 API 테스트")
    void getBalanceTest() throws Exception {
        // given
        long userId = 1L;

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/balance/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print());

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.amount").value(50000));
    }
} 