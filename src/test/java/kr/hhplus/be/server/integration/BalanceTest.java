package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.adapter.locking.InMemoryLockingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("잔액 API 통합 테스트")
public class BalanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private BalanceRepositoryPort balanceRepositoryPort;

    @Autowired
    private InMemoryLockingAdapter lockingAdapter;

    private User userWithBalance;
    private User userWithoutBalance;

    @BeforeEach
    void setUp() {
        // 테스트 간 락 상태 클리어
        lockingAdapter.clearAllLocks();
        
        // 잔액이 있는 테스트 사용자
        userWithBalance = userRepositoryPort.save(User.builder().name("User With Balance").build());
        balanceRepositoryPort.save(Balance.builder()
                .user(userWithBalance)
                .amount(new BigDecimal("50000"))
                .build());

        // 잔액이 없는 테스트 사용자
        userWithoutBalance = userRepositoryPort.save(User.builder().name("User Without Balance").build());
    }

    @Nested
    @DisplayName("POST /api/balance/charge - 잔액 충전")
    class ChargeBalance {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("기존에 잔액이 있던 사용자의 잔액을 충전하면 200 OK와 함께 업데이트된 잔액을 반환한다")
            void chargeBalance_Success_ForExistingBalance() throws Exception {
                // given
                long userId = userWithBalance.getId();
                BigDecimal amount = new BigDecimal("10000");
                BalanceRequest request = new BalanceRequest(userId, amount);

                // when & then
                mockMvc.perform(post("/api/balance/charge")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.userId").value(userId))
                        .andExpect(jsonPath("$.data.amount").value(60000.0)); // 50000 + 10000
            }

            @Test
            @DisplayName("기존에 잔액이 없던 사용자의 잔액을 충전하면 200 OK와 함께 충전된 잔액을 반환한다")
            void chargeBalance_Success_ForNewBalance() throws Exception {
                // given
                long userId = userWithoutBalance.getId();
                BigDecimal amount = new BigDecimal("30000");
                BalanceRequest request = new BalanceRequest(userId, amount);

                // when & then
                mockMvc.perform(post("/api/balance/charge")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.userId").value(userId))
                        .andExpect(jsonPath("$.data.amount").value(30000.0));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            @DisplayName("존재하지 않는 사용자 ID로 요청 시 에러를 반환한다")
            void chargeBalance_UserNotFound() throws Exception {
                // given
                long nonExistentUserId = 999999L;
                BigDecimal amount = new BigDecimal("10000");
                BalanceRequest request = new BalanceRequest(nonExistentUserId, amount);

                // when & then
                mockMvc.perform(post("/api/balance/charge")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().is4xxClientError())
                        .andExpect(jsonPath("$.code").exists())
                        .andExpect(jsonPath("$.message").exists())
                        .andExpect(jsonPath("$.data").isEmpty());
            }

            @Test
            @DisplayName("0원 충전 요청 시 에러를 반환한다")
            void chargeBalance_ZeroAmount() throws Exception {
                // given
                long userId = userWithBalance.getId();
                BigDecimal amount = BigDecimal.ZERO;
                BalanceRequest request = new BalanceRequest(userId, amount);

                // when & then
                mockMvc.perform(post("/api/balance/charge")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().is4xxClientError())
                        .andExpect(jsonPath("$.code").exists())
                        .andExpect(jsonPath("$.message").exists())
                        .andExpect(jsonPath("$.data").isEmpty());
            }

            @Test
            @DisplayName("음수 충전 요청 시 에러를 반환한다")
            void chargeBalance_NegativeAmount() throws Exception {
                // given
                long userId = userWithBalance.getId();
                BigDecimal amount = new BigDecimal("-1000");
                BalanceRequest request = new BalanceRequest(userId, amount);

                // when & then
                mockMvc.perform(post("/api/balance/charge")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().is4xxClientError())
                        .andExpect(jsonPath("$.code").exists())
                        .andExpect(jsonPath("$.message").exists())
                        .andExpect(jsonPath("$.data").isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("GET /api/balance/{userId} - 잔액 조회")
    class GetBalance {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("존재하는 사용자의 ID로 요청 시 200 OK와 함께 현재 잔액을 반환한다")
            void getBalance_Success() throws Exception {
                // given
                long userId = userWithBalance.getId();

                // when & then
                mockMvc.perform(get("/api/balance/{userId}", userId))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.userId").value(userId))
                        .andExpect(jsonPath("$.data.amount").value(50000.0));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 사용자 ID로 요청 시 404 Not Found를 반환한다")
            void getBalance_UserNotFound() throws Exception {
                // given
                long nonExistentUserId = 999L;

                // when & then
                mockMvc.perform(get("/api/balance/{userId}", nonExistentUserId))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USER_ID.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }

            @Test
            @DisplayName("사용자는 존재하지만 잔액 정보가 없을 경우 404 Not Found를 반환한다")
            void getBalance_BalanceNotFound() throws Exception {
                // given
                long userId = userWithoutBalance.getId();

                // when & then
                mockMvc.perform(get("/api/balance/{userId}", userId))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USER_ID.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }
}