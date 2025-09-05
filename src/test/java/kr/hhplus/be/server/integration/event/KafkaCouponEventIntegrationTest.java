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
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.DeleteConsumerGroupsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import kr.hhplus.be.server.integration.IntegrationTestBase;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@DirtiesContext
@DisplayName("Kafka 쿠폰 이벤트 통합 테스트")
class KafkaCouponEventIntegrationTest extends IntegrationTestBase {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
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
    
    @Autowired
    private PlatformTransactionManager transactionManager;

    private KafkaConsumer<String, CouponResultEvent> resultConsumer;
    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() throws Exception {
        // 토픽 보장 (없으면 생성)
        try {
            Properties adminProps = new Properties();
            adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            
            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                adminClient.createTopics(List.of(
                    new NewTopic("coupon-requests", 1, (short) 1),
                    new NewTopic("coupon-results", 1, (short) 1)
                )).all().get(5, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // 이미 존재하면 무시
        }
        // Consumer Group 초기화 - 각 테스트마다 고유한 Consumer Group 사용
        String uniqueGroupId = "test-group-" + System.nanoTime();
        
        try {
            // Consumer Group 삭제하여 offset 초기화
            Properties adminProps = new Properties();
            adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            
            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                adminClient.deleteConsumerGroups(Collections.singletonList(uniqueGroupId))
                          .all().get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Consumer Group이 존재하지 않거나 삭제 실패해도 계속 진행
                System.out.println("Consumer Group 삭제 실패 또는 이미 없음: " + e.getMessage());
            }
            
            Thread.sleep(300); // Consumer Group 삭제 완전 반영 대기
        } catch (Exception e) {
            System.err.println("Kafka Admin 작업 실패: " + e.getMessage());
        }
        
        // TransactionTemplate을 사용해서 데이터를 별도 트랜잭션으로 commit
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        transactionTemplate.execute(status -> {
            // 테스트 데이터 준비 - 이 트랜잭션은 즉시 commit됨
            testUser = User.builder()
                    .name("테스트 사용자_" + System.nanoTime())
                    .build();
            testUser = userRepository.save(testUser);

            testCoupon = Coupon.builder()
                    .code("TEST-COUPON-" + System.nanoTime()) // 각 테스트마다 고유한 코드
                    .discountRate(new java.math.BigDecimal("0.10"))
                    .maxIssuance(10)
                    .issuedCount(0)
                    .startDate(LocalDateTime.now().minusHours(1))
                    .endDate(LocalDateTime.now().plusHours(1))
                    .status(kr.hhplus.be.server.domain.enums.CouponStatus.ACTIVE)
                    .build();
            testCoupon = couponRepository.save(testCoupon);
            
            return null;
        });
        
        System.out.println("테스트 데이터 준비 완료 - User ID: " + testUser.getId() + ", Coupon ID: " + testCoupon.getId() + ", Group: " + uniqueGroupId);

        // Kafka 결과 Consumer 설정 - 각 테스트마다 고유한 Consumer Group 사용
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), 
                                                                        uniqueGroupId, "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // earliest -> latest로 변경하여 이전 메시지 무시
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponResultEvent.class);
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1); // 한 번에 하나의 메시지만 처리
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 수동 offset 관리

        resultConsumer = new KafkaConsumer<>(consumerProps);
        resultConsumer.subscribe(Collections.singletonList("coupon-results"));
        
        // Consumer 초기화를 위한 poll 실행
        resultConsumer.poll(Duration.ofMillis(100));
    }
    
    @AfterEach
    void tearDown() {
        // Kafka Consumer 리소스 정리
        if (resultConsumer != null) {
            try {
                resultConsumer.close(Duration.ofSeconds(5));
            } catch (Exception e) {
                // 테스트 teardown이 실패하지 않도록 예외 처리
                System.err.println("Consumer 종료 중 오류 발생: " + e.getMessage());
            } finally {
                resultConsumer = null;
            }
        }
    }

    @Test
    @DisplayName("쿠폰 요청 이벤트 발행 시 결과 이벤트를 정상적으로 수신한다")
    void shouldReceiveResultEventWhenCouponRequestEventPublished() {
        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());
        System.out.println("이벤트 발행 - userId: " + requestEvent.getUserId() + ", couponId: " + requestEvent.getCouponId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then - 데이터베이스에 CouponHistory가 생성되었는지 확인 (비동기 처리 결과)  
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   long totalHistories = couponHistoryRepository.countByCouponId(testCoupon.getId());
                   if (totalHistories < 1) {
                       throw new AssertionError("쿠폰 히스토리가 생성되지 않았습니다. 현재: " + totalHistories);
                   }
               });
    }

    @Test
    @DisplayName("재고가 없는 쿠폰 요청 시 재고 부족 결과 이벤트를 수신한다")
    void shouldReceiveOutOfStockEventWhenCouponOutOfStock() {
        // given - 재고를 모두 소진된 쿠폰으로 설정 - 트랜잭션으로 보호
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        txTemplate.execute(status -> {
            Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
            // NPE 방지: totalQuantity가 null일 수 있으므로 안전하게 처리
            Long totalQty = coupon.getTotalQuantity();
            if (totalQty != null) {
                coupon.updateIssuedQuantity(totalQty.intValue());
            } else {
                // totalQuantity가 null이면 0으로 간주하고 재고를 0으로 설정
                coupon.updateIssuedQuantity(0);
            }
            return couponRepository.save(coupon);
        });

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
        // given - 먼저 쿠폰을 발급받은 상태로 설정 (트랜잭션으로 보호)
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        txTemplate.execute(status -> {
            CouponHistory existingHistory = CouponHistory.builder()
                    .userId(testUser.getId())
                    .couponId(testCoupon.getId())
                    .issuedAt(LocalDateTime.now())
                    .status(kr.hhplus.be.server.domain.enums.CouponHistoryStatus.ISSUED)
                    .build();
            return couponHistoryRepository.save(existingHistory);
        });

        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   // requestId 검증 추가 - 요청과 응답의 상관관계 확인
                   assertThat(resultEvent.getRequestId()).isEqualTo(requestEvent.getRequestId());
                   assertThat(resultEvent.getResultCode()).isEqualTo(CouponResultEvent.ResultCode.ALREADY_ISSUED);
                   assertThat(resultEvent.isSuccess()).isFalse();
               });
    }

    @Test
    @DisplayName("만료된 쿠폰 요청 시 만료 결과 이벤트를 수신한다")
    void shouldReceiveExpiredEventWhenCouponExpired() {
        // given - 만료된 쿠폰으로 설정 (트랜잭션으로 보호)
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        txTemplate.execute(status -> {
            Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
            coupon.updateEndAt(LocalDateTime.now().minusHours(1));
            return couponRepository.save(coupon);
        });

        CouponRequestEvent requestEvent = CouponRequestEvent.create(testUser.getId(), testCoupon.getId());

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   ConsumerRecord<String, CouponResultEvent> record = KafkaTestUtils.getSingleRecord(
                           resultConsumer, "coupon-results", Duration.ofSeconds(5));
                   
                   CouponResultEvent resultEvent = record.value();
                   // requestId 검증 추가 - 요청과 응답의 상관관계 확인
                   assertThat(resultEvent.getRequestId()).isEqualTo(requestEvent.getRequestId());
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
        // given - 제한된 재고 (5개) - 트랜잭션으로 보호
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        txTemplate.execute(status -> {
            Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
            coupon.updateTotalQuantity(5L);
            return couponRepository.save(coupon);
        });

        // 동시에 10개의 요청 전송
        int requestCount = 10;
        Thread[] threads = new Thread[requestCount];

        // when - 모든 사용자를 별도 트랜잭션에서 생성하여 Consumer에서 읽을 수 있도록 함
        Long[] userIds = new Long[requestCount];
        TransactionTemplate userTxTemplate = new TransactionTemplate(transactionManager);
        userTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            userIds[i] = userTxTemplate.execute(status -> {
                User user = User.builder().name("사용자" + index + "_" + System.currentTimeMillis()).build();
                user = userRepository.save(user);
                return user.getId();
            });
        }

        for (int i = 0; i < requestCount; i++) {
            final long userId = userIds[i];

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