package kr.hhplus.be.server.integration.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.event.CouponResultEvent;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.integration.IntegrationTestBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@DirtiesContext
@DisplayName("JsonDeserializer 동작 확인 테스트")
class JsonDeserializerTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(JsonDeserializerTest.class);

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

    private KafkaConsumer<String, CouponRequestEvent> typedConsumer;

    @BeforeEach
    void setUp() {
        // CouponRequestEvent를 직접 역직렬화하는 Consumer 설정
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), 
                                                                        "typed-group", "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // TYPE_MAPPINGS 설정
        consumerProps.put(JsonDeserializer.TYPE_MAPPINGS, 
            "coupon-requests:kr.hhplus.be.server.domain.event.CouponRequestEvent");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponRequestEvent.class);

        typedConsumer = new KafkaConsumer<>(consumerProps);
        typedConsumer.subscribe(Collections.singletonList("coupon-requests"));
    }

    @Test
    @DisplayName("CouponRequestEvent가 제대로 역직렬화되는지 확인")
    void shouldDeserializeCouponRequestEventCorrectly() {
        // given
        CouponRequestEvent requestEvent = CouponRequestEvent.create(1L, 100L);
        
        log.info("발행할 이벤트: {}", requestEvent);

        // when
        eventPort.publish("coupon-requests", requestEvent);

        // then
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   var record = KafkaTestUtils.getSingleRecord(typedConsumer, "coupon-requests", Duration.ofSeconds(3));
                   
                   log.info("수신된 레코드 - Key: {}, Value Type: {}", record.key(), record.value().getClass());
                   
                   CouponRequestEvent receivedEvent = record.value();
                   assertThat(receivedEvent).isNotNull();
                   assertThat(receivedEvent.getUserId()).isEqualTo(1L);
                   assertThat(receivedEvent.getCouponId()).isEqualTo(100L);
                   assertThat(receivedEvent.getRequestId()).isEqualTo(requestEvent.getRequestId());
                   
                   log.info("역직렬화 성공: {}", receivedEvent);
               });
    }
}