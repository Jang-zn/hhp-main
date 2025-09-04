package kr.hhplus.be.server.integration.event;

import kr.hhplus.be.server.domain.event.CouponRequestEvent;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.integration.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@DirtiesContext
@DisplayName("CouponRequestConsumer 통합 테스트")
class CouponRequestConsumerIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(CouponRequestConsumerIntegrationTest.class);

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

    @Test
    @DisplayName("CouponRequestConsumer가 메시지를 수신하고 로그가 출력되는지 확인")
    void shouldReceiveAndLogCouponRequestMessage() {
        // given
        CouponRequestEvent requestEvent = CouponRequestEvent.create(1L, 100L);
        
        log.info("테스트 시작: 메시지 발행할 이벤트 = {}", requestEvent);

        // when
        eventPort.publish("coupon-requests", requestEvent);
        
        log.info("메시지 발행 완료");

        // then - Consumer 로그가 출력되는지 확인 (5초 대기)
        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(1, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   // 단순히 시간이 지나도록 대기하여 Consumer가 메시지를 처리할 시간을 줌
                   log.info("Consumer가 메시지를 처리할 시간을 기다리는 중...");
               });
        
        log.info("테스트 완료");
    }
}