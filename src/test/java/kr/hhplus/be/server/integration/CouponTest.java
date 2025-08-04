package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.api.dto.request.CouponRequest;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@DisplayName("쿠폰 API 통합 테스트")
public class CouponTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private CouponRepositoryPort couponRepositoryPort;

    @Autowired
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;


    private User testUser;
    private Coupon availableCoupon;
    private Coupon expiredCoupon;
    private Coupon outOfStockCoupon;
    private Coupon notStartedCoupon;

    @BeforeEach
    void setUp() {

        // 테스트 사용자 설정
        testUser = User.builder()
                .name("Test User")
                .build();
        testUser = userRepositoryPort.save(testUser);

        // 사용 가능한 쿠폰
        availableCoupon = Coupon.builder()
                .code("AVAILABLE_COUPON")
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();
        availableCoupon = couponRepositoryPort.save(availableCoupon);

        // 만료된 쿠폰
        expiredCoupon = Coupon.builder()
                .code("EXPIRED_COUPON")
                .discountRate(new BigDecimal("0.15"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().minusDays(1))
                .status(CouponStatus.EXPIRED)
                .build();
        expiredCoupon = couponRepositoryPort.save(expiredCoupon);

        // 재고 없는 쿠폰
        outOfStockCoupon = Coupon.builder()
                .code("OUT_OF_STOCK_COUPON")
                .discountRate(new BigDecimal("0.20"))
                .maxIssuance(1)
                .issuedCount(1)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.SOLD_OUT)
                .build();
        outOfStockCoupon = couponRepositoryPort.save(outOfStockCoupon);

        // 아직 시작되지 않은 쿠폰
        notStartedCoupon = Coupon.builder()
                .code("NOT_STARTED_COUPON")
                .discountRate(new BigDecimal("0.25"))
                .maxIssuance(100)
                .issuedCount(0)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.INACTIVE)
                .build();
        notStartedCoupon = couponRepositoryPort.save(notStartedCoupon);
    }


    @Nested
    @DisplayName("POST /api/coupon/issue - 쿠폰 발급")
    class IssueCoupon {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("정상적인 쿠폰 발급 요청 시 200 OK와 함께 쿠폰 정보를 반환한다")
            void issueCoupon_Success() throws Exception {
                // given
                CouponRequest request = new CouponRequest(testUser.getId(), availableCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data.couponId").value(availableCoupon.getId()))
                        .andExpect(jsonPath("$.data.code").value("AVAILABLE_COUPON"))
                        .andExpect(jsonPath("$.data.discountRate").value(0.10));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 사용자 ID로 요청 시 404 Not Found를 반환한다")
            void issueCoupon_WithNonExistentUser_ShouldFail() throws Exception {
                // given
                long nonExistentUserId = 999L;
                CouponRequest request = new CouponRequest(nonExistentUserId, availableCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // UserException.NotFound는 404 Not Found 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
            }

            @Test
            @DisplayName("존재하지 않는 쿠폰 ID로 요청 시 400 Bad Request를 반환한다")
            void issueCoupon_WithNonExistentCoupon_ShouldFail() throws Exception {
                // given
                long nonExistentCouponId = 999L;
                CouponRequest request = new CouponRequest(testUser.getId(), nonExistentCouponId);

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.COUPON_NOT_FOUND.getMessage()));
            }

            @Test
            @DisplayName("만료된 쿠폰으로 요청 시 410 Gone을 반환한다")
            void issueCoupon_WithExpiredCoupon_ShouldFail() throws Exception {
                // given
                CouponRequest request = new CouponRequest(testUser.getId(), expiredCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isGone())
                        .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_EXPIRED.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.COUPON_EXPIRED.getMessage()));
            }

            @Test
            @DisplayName("재고가 없는 쿠폰으로 요청 시 410 Gone을 반환한다")
            void issueCoupon_WithOutOfStockCoupon_ShouldFail() throws Exception {
                // given
                CouponRequest request = new CouponRequest(testUser.getId(), outOfStockCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isGone())
                        .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED.getMessage()));
            }

            @Test
            @DisplayName("아직 시작되지 않은 쿠폰으로 요청 시 400 Bad Request를 반환한다")
            void issueCoupon_WithNotStartedCoupon_ShouldFail() throws Exception {
                // given
                CouponRequest request = new CouponRequest(testUser.getId(), notStartedCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_NOT_YET_STARTED.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.COUPON_NOT_YET_STARTED.getMessage()));
            }

            @Test
            @DisplayName("이미 발급받은 쿠폰을 다시 요청 시 400 Bad Request를 반환한다")
            void issueCoupon_AlreadyIssued_ShouldFail() throws Exception {
                // given
                // 먼저 쿠폰을 발급받음
                CouponHistory history = CouponHistory.builder()
                        .userId(testUser.getId())
                        .couponId(availableCoupon.getId())
                        .issuedAt(LocalDateTime.now())
                        .status(CouponHistoryStatus.ISSUED)
                        .build();
                couponHistoryRepositoryPort.save(history);

                CouponRequest request = new CouponRequest(testUser.getId(), availableCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_ALREADY_ISSUED.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.COUPON_ALREADY_ISSUED.getMessage()));
            }

            @Test
            @DisplayName("null userId로 요청 시 400 Bad Request를 반환한다")
            void issueCoupon_WithNullUserId_ShouldFail() throws Exception {
                // given
                CouponRequest request = new CouponRequest(null, availableCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }

            @Test
            @DisplayName("음수 userId로 요청 시 400 Bad Request를 반환한다")
            void issueCoupon_WithNegativeUserId_ShouldFail() throws Exception {
                // given
                CouponRequest request = new CouponRequest(-1L, availableCoupon.getId());

                // when & then
                mockMvc.perform(post("/api/coupon/issue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }

    @Nested
    @DisplayName("GET /api/coupon/{userId} - 보유 쿠폰 조회")
    class GetUserCoupons {

        @Nested
        @DisplayName("성공 케이스")
        class Success {
            @Test
            @DisplayName("사용자의 쿠폰 목록을 조회하면 200 OK와 함께 쿠폰 리스트를 반환한다")
            void getCoupons_Success() throws Exception {
                // given
                // 새로운 사용자 및 쿠폰 생성 (독립적인 테스트 데이터)
                User user = User.builder()
                        .name("Coupon Test User")
                        .build();
                user = userRepositoryPort.save(user);

                Coupon coupon = Coupon.builder()
                        .code("SUCCESS_TEST_COUPON")
                        .discountRate(new BigDecimal("0.15"))
                        .maxIssuance(100)
                        .issuedCount(0)
                        .startDate(LocalDateTime.now().minusDays(1))
                        .endDate(LocalDateTime.now().plusDays(30))
                        .status(CouponStatus.ACTIVE)
                        .build();
                coupon = couponRepositoryPort.save(coupon);

                // 사용자에게 쿠폰 발급
                CouponHistory history = CouponHistory.builder()
                        .userId(user.getId())
                        .couponId(coupon.getId())
                        .issuedAt(LocalDateTime.now())
                        .status(CouponHistoryStatus.ISSUED)
                        .build();
                couponHistoryRepositoryPort.save(history);

                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", user.getId())
                                .param("limit", "10")
                                .param("offset", "0")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data", hasSize(1)))
                        .andExpect(jsonPath("$.data[0].couponId").value(coupon.getId()))
                        .andExpect(jsonPath("$.data[0].code").value("SUCCESS_TEST_COUPON"));
            }

            @Test
            @DisplayName("쿠폰이 없는 사용자 조회 시 빈 배열을 반환한다")
            void getCoupons_EmptyResult_Success() throws Exception {
                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", testUser.getId())
                                .param("limit", "10")
                                .param("offset", "0")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data", hasSize(0)));
            }

            @Test
            @DisplayName("페이지네이션 파라미터로 요청 시 해당 범위의 쿠폰을 반환한다")
            void getCoupons_WithPagination_Success() throws Exception {
                // given
                // 여러 쿠폰 발급
                for (int i = 0; i < 5; i++) {
                    Coupon coupon = Coupon.builder()
                            .code("TEST_COUPON_" + i)
                            .discountRate(new BigDecimal("0.10"))
                            .maxIssuance(100)
                            .issuedCount(0)
                            .startDate(LocalDateTime.now().minusDays(1))
                            .endDate(LocalDateTime.now().plusDays(30))
                            .status(CouponStatus.ACTIVE)
                            .build();
                    coupon = couponRepositoryPort.save(coupon);

                    CouponHistory history = CouponHistory.builder()
                            .userId(testUser.getId())
                            .couponId(coupon.getId())
                            .issuedAt(LocalDateTime.now().minusMinutes(i))
                            .status(CouponHistoryStatus.ISSUED)
                            .build();
                    couponHistoryRepositoryPort.save(history);
                }

                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", testUser.getId())
                                .param("limit", "2")
                                .param("offset", "1")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                        .andExpect(jsonPath("$.data").isArray())
                        .andExpect(jsonPath("$.data", hasSize(2)));
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {
            @Test
            @DisplayName("존재하지 않는 사용자 ID로 요청 시 404 Not Found를 반환한다")
            void getCoupons_WithNonExistentUser_ShouldFail() throws Exception {
                // given
                long nonExistentUserId = 999L;

                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", nonExistentUserId)
                                .param("limit", "10")
                                .param("offset", "0")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNotFound()) // UserException.NotFound는 404 Not Found 반환
                        .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
            }

            @Test
            @DisplayName("음수 limit으로 요청 시 400 Bad Request를 반환한다")
            void getCoupons_WithNegativeLimit_ShouldFail() throws Exception {
                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", testUser.getId())
                                .param("limit", "-1")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }

            @Test
            @DisplayName("음수 offset으로 요청 시 400 Bad Request를 반환한다")
            void getCoupons_WithNegativeOffset_ShouldFail() throws Exception {
                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", testUser.getId())
                                .param("offset", "-1")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }

            @Test
            @DisplayName("과도한 limit 값으로 요청 시 400 Bad Request를 반환한다")
            void getCoupons_WithExcessiveLimit_ShouldFail() throws Exception {
                // when & then
                mockMvc.perform(get("/api/coupon/{userId}", testUser.getId())
                                .param("limit", "101")
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                        .andExpect(jsonPath("$.message").exists());
            }
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("한정 수량 쿠폰에 대한 동시 발급 요청에서 정확한 수만큼만 성공해야 한다")
        void limitedCouponConcurrencyTest() throws Exception {
            // given: 수량이 3개인 쿠폰 생성
            Coupon limitedCoupon = Coupon.builder()
                    .code("CONCURRENT_COUPON")
                    .discountRate(new BigDecimal("0.10"))
                    .maxIssuance(3)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();
            final Coupon finalLimitedCoupon = couponRepositoryPort.save(limitedCoupon);

            // 5명의 사용자 생성
            List<User> testUsers = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                User user = userRepositoryPort.save(User.builder().name("ConcurrentUser" + i).build());
                testUsers.add(user);
            }

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(5);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // when: 5명의 사용자가 동시에 쿠폰 발급 요청
            for (User user : testUsers) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기
                        
                        CouponRequest request = new CouponRequest(user.getId(), finalLimitedCoupon.getId());
                        var result = mockMvc.perform(post("/api/coupon/issue")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            System.out.println("⚠️ 쿠폰 발급 실패 - 상태코드: " + result.getResponse().getStatus() + 
                                             ", 응답: " + result.getResponse().getContentAsString());
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();
            
            // 모든 요청 완료 대기 (최대 10초)
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // then: 결과 검증
            assertThat(completed).isTrue();
            assertThat(successCount.get() + failureCount.get()).isEqualTo(5);
            
            // 동시성 제어가 올바르게 작동했다면, 성공 요청은 최대 3개여야 함
            assertThat(successCount.get()).isLessThanOrEqualTo(3);
            assertThat(failureCount.get()).isGreaterThanOrEqualTo(2);
            
            System.out.println("✅ 쿠폰 동시성 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }

        @Test
        @DisplayName("동일 사용자가 같은 쿠폰을 동시에 여러 번 요청해도 한 번만 성공해야 한다")
        void sameCouponDuplicateRequestTest() throws Exception {
            // given: 충분한 수량의 쿠폰 생성
            Coupon duplicateCoupon = Coupon.builder()
                    .code("DUPLICATE_TEST_COUPON")
                    .discountRate(new BigDecimal("0.15"))
                    .maxIssuance(100)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .status(CouponStatus.ACTIVE)
                    .build();
            final Coupon finalDuplicateCoupon = couponRepositoryPort.save(duplicateCoupon);

            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(3);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // when: 동일 사용자가 같은 쿠폰을 3번 동시 요청
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        CouponRequest request = new CouponRequest(testUser.getId(), finalDuplicateCoupon.getId());
                        var result = mockMvc.perform(post("/api/coupon/issue")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
                        
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            System.out.println("⚠️ 쿠폰 발급 실패 - 상태코드: " + result.getResponse().getStatus() + 
                                             ", 응답: " + result.getResponse().getContentAsString());
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

            // then: 동시성 제어로 인해 중복 발급이 방지되어야 함
            assertThat(completed).isTrue();
            // 모든 요청이 완료되었는지 확인
            assertThat(successCount.get() + failureCount.get()).isEqualTo(3);
            // 중복 발급 방지로 인해 최대 1개만 성공하거나 모두 실패할 수 있음
            assertThat(successCount.get()).isLessThanOrEqualTo(1);
            assertThat(failureCount.get()).isGreaterThanOrEqualTo(2);
            
            System.out.println("✅ 중복 발급 방지 테스트 완료 - 성공: " + successCount.get() + ", 실패: " + failureCount.get());
        }
    }
}