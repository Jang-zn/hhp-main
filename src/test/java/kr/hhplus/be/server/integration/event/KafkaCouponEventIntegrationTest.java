package kr.hhplus.be.server.integration.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.event.CouponResultEvent;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.usecase.coupon.IssueCouponUseCase;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
// import kr.hhplus.be.server.test.IntegrationTestBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DirtiesContext
@DisplayName("Kafka 쿠폰 이벤트 통합 테스트")
class KafkaCouponEventIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private EventPort eventPort;

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepositoryPort couponRepository;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private CouponHistoryRepositoryPort couponHistoryRepository;

    private KafkaConsumer<String, CouponResultEvent> resultConsumer;
    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 준비
        testUser = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
        userRepository.save(testUser);

        testCoupon = Coupon.builder()
                .id(100L)
                .code("TEST-COUPON-001")
                .discountRate(new java.math.BigDecimal("0.10"))
                .maxIssuance(10)
                .issuedCount(0)
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .status(kr.hhplus.be.server.domain.enums.CouponStatus.ACTIVE)
                .build();
        couponRepository.save(testCoupon);

        // Kafka 결과 Consumer 설정
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), 
                                                                        "test-group", "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponResultEvent.class);

        resultConsumer = new KafkaConsumer<>(consumerProps);
        resultConsumer.subscribe(Collections.singletonList("coupon-results"));
    }

    @Test
    @DisplayName("쿠폰 요청 이벤트 발행 시 결과 이벤트를 정상적으로 수신한다")
    void shouldReceiveResultEventWhenCouponRequestEventPublished() {
        // given
        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   assertThat(resultEvent.getRequestId()).isEqualTo(requestEvent.getRequestId());
                   assertThat(resultEvent.getUserId()).isEqualTo(testUser.getId());
                   assertThat(resultEvent.getCouponId()).isEqualTo(testCoupon.getId());
                   assertThat(resultEvent.isSuccess()).isTrue();
                   assertThat(resultEvent.getResultCode()).isEqualTo(CouponResultEvent.ResultCode.SUCCESS);
                   assertThat(resultEvent.getCouponHistoryId()).isNotNull();
               });

        // 데이터베이스 확인
        await().atMost(5, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   CouponHistory history = couponHistoryRepository
                           .findByUserIdAndCouponId(testUser.getId(), testCoupon.getId())
                           .orElseThrow(() -> new AssertionError("쿠폰 히스토리가 생성되지 않았습니다"));
                   
                   assertThat(history.getUserId()).isEqualTo(testUser.getId());
                   assertThat(history.getCouponId()).isEqualTo(testCoupon.getId());
               });
    }

    @Test
    @DisplayName("재고가 없는 쿠폰 요청 시 재고 부족 결과 이벤트를 수신한다")
    void shouldReceiveOutOfStockEventWhenCouponOutOfStock() {
        // given - 재고를 모두 소진된 쿠폰으로 설정
        testCoupon.updateIssuedQuantity(testCoupon.getTotalQuantity());
        couponRepository.save(testCoupon);

        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   assertThat(resultEvent.getRequestId()).isEqualTo(requestEvent.getRequestId());
                   assertThat(resultEvent.getUserId()).isEqualTo(testUser.getId());
                   assertThat(resultEvent.getCouponId()).isEqualTo(testCoupon.getId());
                   assertThat(resultEvent.isSuccess()).isFalse();
                   assertThat(resultEvent.getResultCode()).isEqualTo(CouponResultEvent.ResultCode.OUT_OF_STOCK);
                   assertThat(resultEvent.getCouponHistoryId()).isNull();
               });
    }

    @Test
    @DisplayName("동일 사용자가 중복 요청 시 중복 발급 결과 이벤트를 수신한다")
    void shouldReceiveAlreadyIssuedEventWhenDuplicateRequest() {
        // given - 먼저 쿠폰을 발급받은 상태로 설정
        CouponHistory existingHistory = CouponHistory.builder()
                .userId(testUser.getId())
                .couponId(testCoupon.getId())
                .build();
        couponHistoryRepository.save(existingHistory);

        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   assertThat(resultEvent.getResultCode()).isEqualTo(CouponResultEvent.ResultCode.ALREADY_ISSUED);
                   assertThat(resultEvent.isSuccess()).isFalse();
               });
    }

    @Test
    @DisplayName("만료된 쿠폰 요청 시 만료 결과 이벤트를 수신한다")
    void shouldReceiveExpiredEventWhenCouponExpired() {
        // given - 만료된 쿠폰으로 설정
        testCoupon.updateEndAt(LocalDateTime.now().minusHours(1));
        couponRepository.save(testCoupon);

        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   assertThat(resultEvent.getResultCode()).isEqualTo(CouponResultEvent.ResultCode.EXPIRED);
                   assertThat(resultEvent.isSuccess()).isFalse();
               });
    }

    @Test
    @DisplayName("파티션 키가 userId 기반으로 생성되어 동일 사용자 요청은 같은 파티션에 전송된다")
    void shouldSendSameUserRequestsToSamePartition() {
        // given
        CouponRequestEvent requestEvent1 = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());
        CouponRequestEvent requestEvent2 = CouponRequestEvent.create(testUser.getId(), testCoupon.getId() + 1);

        // when
        eventPort.publish("coupon-requests", requestEvent1);
        eventPort.publish("coupon-requests", requestEvent2);

        // then - 파티션 키가 동일한지 확인 (실제 파티션 매핑은 Kafka가 담당)
        assertThat(requestEvent1.getPartitionKey()).isEqualTo("user:" + testUser.getId());
        assertThat(requestEvent2.getPartitionKey()).isEqualTo("user:" + testUser.getId());
        assertThat(requestEvent1.getPartitionKey()).isEqualTo(requestEvent2.getPartitionKey());
    }

    @Test
    @DisplayName("높은 동시성 환경에서 선착순 처리가 정상적으로 작동한다")
    void shouldHandleHighConcurrencyCorrectly() throws InterruptedException {
        // given - 제한된 재고 (5개)
        testCoupon.updateTotalQuantity(5L);
        couponRepository.save(testCoupon);

        // 동시에 10개의 요청 전송
        int requestCount = 10;
        Thread[] threads = new Thread[requestCount];

        // when
        for (int i = 0; i < requestCount; i++) {
            final long userId = i + 1L;
            User user = User.builder().id(userId).name("사용자" + userId).build();
            userRepository.save(user);

            threads[i] = new Thread(() -> {
                CouponRequestEvent requestEvent = CouponRequestEvent.create(userId, testCoupon.getId());
                eventPort.publish("coupon-requests", requestEvent);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // then - 5개만 성공하고 5개는 실패해야 함
        await().atMost(15, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   long successCount = couponHistoryRepository.countByCouponId(testCoupon.getId());
                   assertThat(successCount).isEqualTo(5L);
               });
    }

    @Test
    @DisplayName("처리 시간이 결과 이벤트에 포함된다")
    void shouldIncludeProcessingTimeInResultEvent() {
        // given
        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        long startTime = System.currentTimeMillis();
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   assertThat(resultEvent.getProcessingTimeMs()).isNotNull();
                   assertThat(resultEvent.getProcessingTimeMs()).isPositive();
                   
                   // 처리 시간이 합리적인 범위인지 확인 (최대 5초)
                   long maxExpectedTime = System.currentTimeMillis() - startTime + 1000; // 여유시간 1초
                   assertThat(resultEvent.getProcessingTimeMs()).isLessThan(maxExpectedTime);
               });
    }
}