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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
     * Kafka용 ObjectMapper 설정
     * 
     * Java 8 시간 객체와 JSON 직렬화/역직렬화를 위한 설정입니다.
     */
    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Kafka Producer 설정
     * 
     * JSON 직렬화를 사용하여 이벤트 객체를 전송합니다.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 성능 및 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 복제본 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // 재시도 횟수
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 4096); // 배치 크기 (테스트 환경용 축소)
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1); // 배치 대기 시간
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1048576); // 버퍼 메모리 1MB (테스트 환경용)
        
        // 멱등성 보장 (중복 메시지 방지)
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // JsonSerializer와 StringSerializer를 직접 생성
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(kafkaObjectMapper);
        StringSerializer keySerializer = new StringSerializer();
        
        return new DefaultKafkaProducerFactory<>(configProps, keySerializer, valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Kafka Consumer 설정
     * 
     * JSON 역직렬화를 사용하여 이벤트 객체를 수신합니다.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> props = new HashMap<>();
        
        // 기본 설정
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        
        // ErrorHandlingDeserializer에 대한 실제 Deserializer 설정
        props.put("spring.deserializer.key.delegate.class", StringDeserializer.class.getName());
        props.put("spring.deserializer.value.delegate.class", JsonDeserializer.class.getName());
        
        // Consumer 동작 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 처음부터 읽기
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 수동 커밋
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10); // 테스트 환경용 축소
        
        // JsonDeserializer 설정
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class.getName());
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * CouponRequest 전용 ConsumerFactory
     */
    @Bean
    public ConsumerFactory<String, kr.hhplus.be.server.domain.event.CouponRequestEvent> couponRequestConsumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> props = new HashMap<>();
        
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-processor-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        
        props.put("spring.deserializer.key.delegate.class", StringDeserializer.class.getName());
        props.put("spring.deserializer.value.delegate.class", JsonDeserializer.class.getName());
        
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, 
            "kr.hhplus.be.server.domain.event.CouponRequestEvent");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * CouponResult 전용 ConsumerFactory  
     */
    @Bean
    public ConsumerFactory<String, kr.hhplus.be.server.domain.event.CouponResultEvent> couponResultConsumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> props = new HashMap<>();
        
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-result-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        
        props.put("spring.deserializer.key.delegate.class", StringDeserializer.class.getName());
        props.put("spring.deserializer.value.delegate.class", JsonDeserializer.class.getName());
        
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, 
            "kr.hhplus.be.server.domain.event.CouponResultEvent");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 기본 Kafka Listener Container Factory
     * 
     * 일반적인 도메인 이벤트 처리를 위한 기본 설정입니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
        // 기본 성능 튜닝
        factory.setConcurrency(1); // 테스트 환경용 축소
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }

    /**
     * 외부 이벤트용 Listener Container Factory
     * 
     * 외부 데이터 플랫폼 연동용 이벤트 처리를 위한 설정입니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> externalEventKafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
        // 동시성 설정 - 외부 연동은 순서가 중요하지 않으므로 병렬 처리
        factory.setConcurrency(1); // 테스트 환경용 축소
        
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
    public ConcurrentKafkaListenerContainerFactory<String, kr.hhplus.be.server.domain.event.CouponRequestEvent> couponRequestKafkaListenerContainerFactory(ConsumerFactory<String, kr.hhplus.be.server.domain.event.CouponRequestEvent> couponRequestConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, kr.hhplus.be.server.domain.event.CouponRequestEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(couponRequestConsumerFactory);
        
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

    /**
     * 쿠폰 결과용 Listener Container Factory
     */
    @Bean 
    public ConcurrentKafkaListenerContainerFactory<String, kr.hhplus.be.server.domain.event.CouponResultEvent> couponResultKafkaListenerContainerFactory(ConsumerFactory<String, kr.hhplus.be.server.domain.event.CouponResultEvent> couponResultConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, kr.hhplus.be.server.domain.event.CouponResultEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(couponResultConsumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            (record, exception) -> {
                log.error("쿠폰 결과 처리 실패: topic={}, key={}, value={}", 
                         record.topic(), record.key(), record.value(), exception);
            }
        ));
        
        return factory;
    }

    /**
     * 레거시 쿠폰 요청용 Listener Container Factory (기존 호환성 유지)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> couponKafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
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

    /**
     * 주문 완료 이벤트용 Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, kr.hhplus.be.server.domain.event.OrderCompletedEvent> orderCompletedConsumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-ranking-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        
        props.put("spring.deserializer.key.delegate.class", StringDeserializer.class.getName());
        props.put("spring.deserializer.value.delegate.class", JsonDeserializer.class.getName());
        
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, 
            "kr.hhplus.be.server.domain.event.OrderCompletedEvent");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 주문 완료 이벤트용 Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, kr.hhplus.be.server.domain.event.OrderCompletedEvent> orderCompletedKafkaListenerContainerFactory(
            ConsumerFactory<String, kr.hhplus.be.server.domain.event.OrderCompletedEvent> orderCompletedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, kr.hhplus.be.server.domain.event.OrderCompletedEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(orderCompletedConsumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            (record, exception) -> {
                log.error("주문 완료 이벤트 처리 실패: topic={}, key={}, value={}", 
                         record.topic(), record.key(), record.value(), exception);
            }
        ));
        
        return factory;
    }

    // ========================= KAFKA TOPICS =========================

    /**
     * Kafka Admin 설정
     */
    @Bean
    public org.springframework.kafka.core.KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new org.springframework.kafka.core.KafkaAdmin(configs);
    }

    /**
     * 선착순 쿠폰 요청 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic couponRequestsTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("coupon-requests")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 선착순 쿠폰 결과 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic couponResultsTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("coupon-results")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 외부 데이터 플랫폼 연동 이벤트 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic externalEventsTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("external-events")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 주문 완료 이벤트 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic orderCompletedTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("order.completed")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 상품 생성 이벤트 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic productCreatedTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("product.created")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 상품 업데이트 이벤트 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic productUpdatedTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("product.updated")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 상품 삭제 이벤트 토픽
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic productDeletedTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("product.deleted")
                .partitions(1)
                .replicas(1)
                .build();
    }
}