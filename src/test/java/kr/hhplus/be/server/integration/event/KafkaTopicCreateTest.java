package kr.hhplus.be.server.integration.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.integration.IntegrationTestBase;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
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
@DisplayName("Kafka 토픽 생성 테스트")
class KafkaTopicCreateTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicCreateTest.class);

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

    private AdminClient adminClient;
    private KafkaConsumer<String, String> stringConsumer;

    @BeforeEach
    void setUp() throws Exception {
        // AdminClient 생성
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        adminClient = AdminClient.create(adminProps);

        // 토픽 생성
        List<NewTopic> topics = List.of(
                new NewTopic("coupon-requests", 1, (short) 1),
                new NewTopic("coupon-results", 1, (short) 1)
        );

        try {
            CreateTopicsResult result = adminClient.createTopics(topics);
            result.all().get(10, TimeUnit.SECONDS); // 토픽 생성 대기
            log.info("토픽 생성 완료: coupon-requests, coupon-results");
        } catch (Exception e) {
            log.info("토픽이 이미 존재하거나 생성 중 오류 발생: {}", e.getMessage());
        }

        // Consumer 설정
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), 
                                                                        "test-group", "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        stringConsumer = new KafkaConsumer<>(consumerProps);
        stringConsumer.subscribe(Collections.singletonList("coupon-requests"));
    }

    @AfterEach
    void tearDown() {
        // KafkaConsumer 안전하게 종료
        if (stringConsumer != null) {
            try {
                stringConsumer.close(Duration.ofSeconds(5));
                log.debug("KafkaConsumer 종료 완료");
            } catch (Exception e) {
                log.warn("KafkaConsumer 종료 중 예외 발생: {}", e.getMessage());
            }
        }
        
        // AdminClient 안전하게 종료
        if (adminClient != null) {
            try {
                adminClient.close(Duration.ofSeconds(5));
                log.debug("AdminClient 종료 완료");
            } catch (Exception e) {
                log.warn("AdminClient 종료 중 예외 발생: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("토픽 생성 후 메시지 발행이 성공하는지 확인")
    void shouldPublishMessageAfterTopicCreation() {
        // given
        CouponRequestEvent requestEvent = CouponRequestEvent.create(1L, 100L);
        
        log.info("메시지 발행: {}", requestEvent);

        // when
        eventPort.publish("coupon-requests", requestEvent);
        
        log.info("메시지 발행 완료");

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   var record = KafkaTestUtils.getSingleRecord(stringConsumer, "coupon-requests", Duration.ofSeconds(3));
                   log.info("수신된 메시지 - Key: {}, Value: {}", record.key(), record.value());
                   
                   assertThat(record.key()).isNotNull();
                   assertThat(record.value()).contains("COUPON_REQ");
                   
                   log.info("메시지 수신 성공");
               });
    }
}