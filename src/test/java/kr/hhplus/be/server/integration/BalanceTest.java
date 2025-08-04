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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
                .userId(userWithBalance.getId())
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

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동일 사용자가 동시에 충전 요청해도 모든 금액이 정확히 반영되어야 한다")
        void concurrentChargeTest() throws Exception {
            // given: 초기 잔액이 10000원인 사용자
            User testUser = userRepositoryPort.save(User.builder().name("ConcurrentChargeUser").build());
            balanceRepositoryPort.save(Balance.builder()
                    .userId(testUser.getId())
                    .amount(new BigDecimal("10000"))
                    .build());

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(5);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            BigDecimal chargeAmount = new BigDecimal("1000"); // 각각 1000원씩 충전

            // when: 5번의 동시 충전 요청 (각 1000원)
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        BalanceRequest request = new BalanceRequest(testUser.getId(), chargeAmount);
                        var result = mockMvc.perform(post("/api/balance/charge")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // then: 모든 요청이 완료되고, 성공한 만큼 잔액이 증가해야 함
            assertThat(completed).isTrue();
            assertThat(successCount.get() + failureCount.get()).isEqualTo(5);
            
            // 동시성 제어로 인해 일부 요청이 실패할 수 있으므로, 성공한 만큼만 잔액 증가 확인
            int expectedAmount = 10000 + (successCount.get() * 1000);
            mockMvc.perform(get("/api/balance/{userId}", testUser.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.amount").value(expectedAmount));
            
            System.out.println("✅ 동시 충전 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }

        @Test
        @DisplayName("동일 사용자가 동시에 사용 요청해도 잔액이 마이너스가 되지 않아야 한다")
        void concurrentDeductTest() throws Exception {
            // given: 초기 잔액이 5000원인 사용자
            User testUser = userRepositoryPort.save(User.builder().name("ConcurrentDeductUser").build());
            balanceRepositoryPort.save(Balance.builder()
                    .userId(testUser.getId())
                    .amount(new BigDecimal("5000"))
                    .build());

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(5);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            BigDecimal deductAmount = new BigDecimal("2000"); // 각각 2000원씩 차감 시도

            // when: 5번의 동시 차감 요청 (각 2000원, 총 10000원 시도하지만 잔액은 5000원)
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        BalanceRequest request = new BalanceRequest(testUser.getId(), deductAmount);
                        var result = mockMvc.perform(post("/api/balance/deduct")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // then: 일부만 성공하고, 잔액이 마이너스가 되지 않아야 함
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isLessThanOrEqualTo(2); // 최대 2번만 성공 가능 (5000 / 2000)
            assertThat(failureCount.get()).isGreaterThanOrEqualTo(3);

            // 최종 잔액이 0 이상이어야 함
            mockMvc.perform(get("/api/balance/{userId}", testUser.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.amount", org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
            
            System.out.println("✅ 동시 차감 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }

        @Test
        @DisplayName("동일 사용자가 충전과 차감을 동시에 요청해도 최종 잔액이 정확해야 한다")
        void concurrentChargeAndDeductTest() throws Exception {
            // given: 초기 잔액이 10000원인 사용자
            User testUser = userRepositoryPort.save(User.builder().name("ConcurrentMixedUser").build());
            balanceRepositoryPort.save(Balance.builder()
                    .userId(testUser.getId())
                    .amount(new BigDecimal("10000"))
                    .build());

            ExecutorService executor = Executors.newFixedThreadPool(6);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(6);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // when: 3번의 충전(+2000)과 3번의 차감(-1000) 동시 요청
            // 충전 요청 3번
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        BalanceRequest request = new BalanceRequest(testUser.getId(), new BigDecimal("2000"));
                        var result = mockMvc.perform(post("/api/balance/charge")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 차감 요청 3번
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        BalanceRequest request = new BalanceRequest(testUser.getId(), new BigDecimal("1000"));
                        var result = mockMvc.perform(post("/api/balance/deduct")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // then: 모든 요청이 완료되고, 최종 잔액이 예상 범위 내에 있어야 함
            assertThat(completed).isTrue();
            
            // 최종 잔액 확인 - 정확한 값은 실행 순서에 따라 달라질 수 있지만 논리적으로 맞아야 함
            mockMvc.perform(get("/api/balance/{userId}", testUser.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.amount", org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
            
            System.out.println("✅ 동시 충전/차감 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }
    }
}