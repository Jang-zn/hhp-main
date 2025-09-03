package kr.hhplus.be.server.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정 클래스
 * 
 * Producer와 Consumer 설정을 담당하며, 다음 토픽들을 지원합니다:
 * - external-events: 외부 데이터 플랫폼 연동 이벤트
 * - coupon-requests: 선착순 쿠폰 요청 이벤트
 * - coupon-results: 선착순 쿠폰 처리 결과 이벤트
 */
@Slf4j
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:hhplus-server}")
    private String groupId;

    /**
     * Kafka Producer 설정
     * 
     * JSON 직렬화를 사용하여 이벤트 객체를 전송합니다.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 성능 및 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 복제본 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // 재시도 횟수
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 배치 크기
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1); // 배치 대기 시간
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 버퍼 메모리
        
        // 멱등성 보장 (중복 메시지 방지)
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Consumer 설정
     * 
     * JSON 역직렬화를 사용하여 이벤트 객체를 수신합니다.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // 기본 설정
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // JSON 역직렬화 설정
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "kr.hhplus.be.server.domain.event");
        // JsonDeserializer가 자동으로 타입을 판단하도록 설정
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        
        // Consumer 동작 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 처음부터 읽기
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 수동 커밋
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500); // 최대 폴링 레코드 수
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 외부 이벤트용 Listener Container Factory
     * 
     * 외부 데이터 플랫폼 연동용 이벤트 처리를 위한 설정입니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> externalEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // 동시성 설정 - 외부 연동은 순서가 중요하지 않으므로 병렬 처리
        factory.setConcurrency(3);
        
        // 수동 ACK 모드 - 처리 완료 후 명시적으로 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // 에러 처리 - 실패한 메시지는 로그 출력 후 스킵
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            (record, exception) -> {
                log.error("외부 이벤트 처리 실패: topic={}, key={}, value={}", 
                         record.topic(), record.key(), record.value(), exception);
            }
        ));
        
        return factory;
    }

    /**
     * 쿠폰 요청용 Listener Container Factory
     * 
     * 선착순 쿠폰 처리를 위한 설정입니다.
     * userId 기반 파티셔닝으로 동일 사용자 요청의 순서를 보장합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> couponKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // 동시성 설정 - 파티션별로 순서 보장을 위해 파티션 수와 동일하게 설정
        factory.setConcurrency(1);
        
        // 수동 ACK 모드 - 처리 완료 후 명시적으로 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // 에러 처리 - 쿠폰 처리 실패 시 재시도 후 DLQ 전송
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            (record, exception) -> {
                log.error("쿠폰 요청 처리 실패: topic={}, key={}, value={}", 
                         record.topic(), record.key(), record.value(), exception);
            }
        ));
        
        return factory;
    }
}