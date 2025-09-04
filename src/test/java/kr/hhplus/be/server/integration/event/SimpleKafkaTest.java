package kr.hhplus.be.server.integration.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.integration.IntegrationTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@DirtiesContext
@DisplayName("Kafka 단순 연동 테스트")
class SimpleKafkaTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(SimpleKafkaTest.class);

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

    private KafkaConsumer<String, String> stringConsumer;

    @BeforeEach
    void setUp() {
        // 문자열 Consumer 설정 (디버깅용)
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), 
                                                                        "debug-group", "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        stringConsumer = new KafkaConsumer<>(consumerProps);
        stringConsumer.subscribe(Collections.singletonList("coupon-requests"));
    }

    @Test
    @DisplayName("Kafka에 메시지가 제대로 발행되는지 확인")
    void shouldPublishMessageToKafka() {
        // given
        CouponRequestEvent requestEvent = CouponRequestEvent.create(1L, 100L);
        
        log.info("메시지 발행 시작: {}", requestEvent);

        // when
        eventPort.publish("coupon-requests", requestEvent);
        
        log.info("메시지 발행 완료");

        // then - 원시 문자열로 메시지가 도착하는지 확인
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   var record = KafkaTestUtils.getSingleRecord(stringConsumer, "coupon-requests", Duration.ofSeconds(3));
                   log.info("수신된 메시지 - Key: {}, Value: {}", record.key(), record.value());
                   
                   // JSON 문자열인지 확인
                   String jsonValue = record.value();
                   log.info("JSON 내용: {}", jsonValue);
               });
    }
}