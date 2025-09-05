# STEP 18: 카프카를 활용한 비즈니스 프로세스 개선 설계문서

## 📋 개요

본 문서는 Apache Kafka를 활용하여 구현한 이벤트 드리븐 아키텍처와 비즈니스 프로세스를 설명합니다.

---

## 🎯 구현 목표

### 주요 구현 사항
- **이벤트 드리븐 아키텍처**: 비동기 메시지 처리
- **파티셔닝**: userId 기반 메시지 순서 보장  
- **Consumer 구현**: 각 비즈니스 도메인별 이벤트 처리
- **외부 시스템 연동**: 비동기 데이터 전송

---

## 🏗️ 시스템 아키텍처

### 전체 아키텍처 다이어그램

```mermaid
graph TB
    subgraph "Client Layer"
        WEB[Web Client]
        APP[Mobile App]
    end

    subgraph "API Gateway Layer"
        GATEWAY[API Gateway]
    end

    subgraph "Application Layer"
        USER_API[User Service]
        COUPON_API[Coupon Service]
        ORDER_API[Order Service]
        PRODUCT_API[Product Service]
    end

    subgraph "Kafka Cluster"
        BROKER1[Kafka Broker 1]
        BROKER2[Kafka Broker 2]
        BROKER3[Kafka Broker 3]
        
        subgraph "Topics"
            TOPIC_COUPON_REQ[coupon-requests<br/>Partitions: 3]
            TOPIC_COUPON_RES[coupon-results<br/>Partitions: 3]
            TOPIC_ORDER[order-completed<br/>Partitions: 3]
            TOPIC_EXTERNAL[external-events<br/>Partitions: 3]
        end
    end

    subgraph "Consumer Applications"
        COUPON_CONSUMER[Coupon Request Consumer]
        RESULT_CONSUMER[Coupon Result Consumer]
        ORDER_CONSUMER[Order Completed Consumer]
        EXTERNAL_CONSUMER[External Event Consumer]
        PRODUCT_CONSUMER[Product Event Consumer]
    end

    subgraph "Data Stores"
        MYSQL[(MySQL<br/>Primary DB)]
        REDIS[(Redis<br/>Cache & Lock)]
        EXTERNAL_SYS[External Systems<br/>Analytics Platform]
    end


    %% Client to API
    WEB --> GATEWAY
    APP --> GATEWAY
    GATEWAY --> USER_API
    GATEWAY --> COUPON_API
    GATEWAY --> ORDER_API
    GATEWAY --> PRODUCT_API

    %% API to Kafka
    COUPON_API -.->|publish| TOPIC_COUPON_REQ
    ORDER_API -.->|publish| TOPIC_ORDER
    COUPON_API -.->|publish| TOPIC_EXTERNAL

    %% Kafka Internal
    TOPIC_COUPON_REQ --> BROKER1
    TOPIC_COUPON_RES --> BROKER2
    TOPIC_ORDER --> BROKER3
    TOPIC_EXTERNAL --> BROKER1

    %% Kafka to Consumers
    TOPIC_COUPON_REQ --> COUPON_CONSUMER
    TOPIC_COUPON_RES --> RESULT_CONSUMER
    TOPIC_ORDER --> ORDER_CONSUMER
    TOPIC_ORDER --> PRODUCT_CONSUMER
    TOPIC_EXTERNAL --> EXTERNAL_CONSUMER

    %% Consumers to Data Stores
    COUPON_CONSUMER --> MYSQL
    COUPON_CONSUMER --> REDIS
    ORDER_CONSUMER --> REDIS
    PRODUCT_CONSUMER --> REDIS
    EXTERNAL_CONSUMER --> EXTERNAL_SYS

    %% Consumer to Kafka (result publishing)
    COUPON_CONSUMER -.->|publish result| TOPIC_COUPON_RES


    %% Styling
    classDef apiService fill:#e1f5fe,stroke:#0277bd,stroke-width:2px
    classDef kafkaService fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef consumerService fill:#e8f5e8,stroke:#2e7d32,stroke-width:2px
    classDef dataStore fill:#fff3e0,stroke:#ef6c00,stroke-width:2px

    class USER_API,COUPON_API,ORDER_API,PRODUCT_API apiService
    class BROKER1,BROKER2,BROKER3,TOPIC_COUPON_REQ,TOPIC_COUPON_RES,TOPIC_ORDER,TOPIC_EXTERNAL kafkaService
    class COUPON_CONSUMER,RESULT_CONSUMER,ORDER_CONSUMER,EXTERNAL_CONSUMER,PRODUCT_CONSUMER consumerService
    class MYSQL,REDIS,EXTERNAL_SYS dataStore
```

---

## 🔄 비즈니스 시퀀스 다이어그램

### 1. 선착순 쿠폰 발급 프로세스

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Coupon API
    participant KP as Kafka Producer
    participant KB as Kafka Broker
    participant CC as Coupon Consumer
    participant DB as MySQL
    participant REDIS as Redis Cache
    participant RC as Result Consumer
    participant KC as Kafka Consumer

    Note over C,KC: 선착순 쿠폰 발급 프로세스

    %% 요청 단계
    C->>+API: POST /coupons/issue<br/>{userId: 123, couponId: 456}
    API->>+DB: EventLog 생성<br/>PENDING 상태로 저장
    API->>+KP: CouponRequestEvent 발행
    Note right of KP: key="user:123"<br/>partition=hash(userId)%3
    KP->>+KB: 메시지 전송<br/>coupon-requests 토픽
    KB-->>-KP: ACK 응답
    API-->>-C: 202 Accepted<br/>{requestId: "req-789"}

    %% 비동기 처리 단계
    KB->>+CC: CouponRequestEvent 소비<br/>partition=0 (user:123)
    Note right of CC: 같은 userId는<br/>항상 같은 파티션에서<br/>순차 처리됨

    CC->>+REDIS: 분산 락 획득<br/>key="coupon:456:user:123"
    REDIS-->>-CC: 락 획득 성공

    CC->>+DB: 쿠폰 발급 검증<br/>- 재고 확인<br/>- 중복 발급 확인<br/>- 유효 기간 확인
    
    alt 발급 가능한 경우
        DB->>+DB: CouponHistory 생성<br/>Coupon 재고 차감
        DB-->>-CC: 발급 성공
        CC->>+KP: CouponResultEvent 발행
        Note right of KP: SUCCESS 결과<br/>key="user:123"
        KP->>KB: coupon-results 토픽에 전송
        CC->>+DB: EventLog 상태<br/>COMPLETED로 업데이트
        DB-->>-CC: 업데이트 완료
    else 발급 불가능한 경우
        DB-->>-CC: 발급 실패<br/>(재고부족/중복발급/만료)
        CC->>+KP: CouponResultEvent 발행
        Note right of KP: FAILED 결과<br/>key="user:123"<br/>reason="OUT_OF_STOCK"
        KP->>KB: coupon-results 토픽에 전송
        CC->>+DB: EventLog 상태<br/>FAILED로 업데이트
        DB-->>-CC: 업데이트 완료
    end

    CC->>+REDIS: 분산 락 해제
    REDIS-->>-CC: 락 해제 완료
    CC->>KB: 오프셋 커밋<br/>메시지 처리 완료

    %% 결과 처리 단계
    KB->>+RC: CouponResultEvent 소비
    RC->>+KC: 실시간 알림 전송<br/>WebSocket/SSE
    KC-->>-C: 쿠폰 발급 결과 알림<br/>{success: true, couponHistoryId: 999}
    RC->>+DB: EventLog 업데이트<br/>NOTIFIED 상태
    DB-->>-RC: 업데이트 완료

    Note over C,KC: userId 기반 파티셔닝으로 순서 보장<br/>비동기 이벤트 처리
```

### 2. 주문 완료 후 상품 랭킹 업데이트 프로세스

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Order API
    participant TX as TransactionManager
    participant KP as Kafka Producer
    participant KB as Kafka Broker
    participant OC as Order Consumer
    participant PC as Product Consumer
    participant REDIS as Redis Cache
    participant ANALYTICS as Analytics Platform

    Note over C,ANALYTICS: 주문 완료 후 실시간 상품 랭킹 업데이트

    %% 주문 결제 단계
    C->>+API: POST /orders/{orderId}/pay<br/>{paymentInfo}
    API->>+TX: 트랜잭션 시작
    
    TX->>+TX: 주문 상태 변경<br/>PENDING → COMPLETED
    TX->>+TX: 결제 정보 저장<br/>Payment 엔티티 생성
    TX->>+TX: 재고 차감<br/>Product.stock -= quantity
    
    TX-->>-API: 트랜잭션 커밋 완료
    
    Note right of API: 트랜잭션 완료 후<br/>이벤트 발행 (트랜잭션 경계 외부)
    
    API->>+KP: OrderCompletedEvent 발행
    Note right of KP: {orderId: 123,<br/>userId: 456,<br/>products: [{id:1, qty:2}],<br/>totalAmount: 50000}
    
    KP->>+KB: order-completed 토픽에 전송
    KB-->>-KP: ACK 응답
    API-->>-C: 200 OK<br/>{paymentId: 789, status: "COMPLETED"}

    %% 비동기 이벤트 처리
    par 상품 랭킹 업데이트
        KB->>+PC: OrderCompletedEvent 소비<br/>Product Consumer
        PC->>+REDIS: 상품별 주문 수량 업데이트<br/>ZINCRBY ranking:products {productId} {quantity}
        Note right of REDIS: Redis Sorted Set<br/>실시간 랭킹 관리
        REDIS-->>-PC: 랭킹 업데이트 완료
        PC->>KB: 오프셋 커밋
    and 주문 분석 데이터 전송
        KB->>+OC: OrderCompletedEvent 소비<br/>Order Consumer
        OC->>+ANALYTICS: 주문 데이터 전송<br/>ETL 파이프라인으로 전달
        Note right of ANALYTICS: - 매출 분석<br/>- 고객 행동 분석<br/>- 상품 선호도 분석
        ANALYTICS-->>-OC: 데이터 전송 완료
        OC->>KB: 오프셋 커밋
    end

    Note over C,ANALYTICS: 이벤트 기반 비동기 처리<br/>실시간 랭킹 업데이트
```

### 3. 외부 시스템 연동 프로세스

```mermaid
sequenceDiagram
    participant API as Service API
    participant KP as Kafka Producer
    participant KB as Kafka Broker
    participant EC as External Consumer
    participant EXT as External System
    participant DB as EventLog DB
    participant RETRY as Retry Handler

    Note over API,RETRY: 외부 시스템 연동 (데이터 플랫폼, 알림 서비스 등)

    %% 이벤트 발행
    API->>+KP: 비즈니스 이벤트 발행<br/>PaymentCompletedEvent
    Note right of KP: {paymentId: 123,<br/>orderId: 456,<br/>amount: 50000,<br/>timestamp: "2024-09-05T10:30:00"}
    
    KP->>+KB: external-events 토픽에 전송
    KB-->>-KP: ACK 응답
    KP->>+DB: EventLog 저장<br/>PUBLISHED 상태
    DB-->>-KP: 저장 완료

    %% 외부 시스템 연동
    KB->>+EC: External Event 소비
    EC->>+DB: EventLog 상태 업데이트<br/>IN_PROGRESS
    DB-->>-EC: 업데이트 완료

    EC->>+EXT: HTTP API 호출<br/>POST /webhooks/payment-completed
    
    alt 외부 시스템 응답 성공
        EXT-->>-EC: 200 OK<br/>{processed: true}
        EC->>+DB: EventLog 상태<br/>COMPLETED로 업데이트
        DB-->>-EC: 업데이트 완료
        EC->>KB: 오프셋 커밋
    else 외부 시스템 응답 실패
        EXT-->>EC: 5xx Error<br/>또는 Timeout
        EC->>+DB: EventLog 상태<br/>FAILED로 업데이트<br/>재시도 횟수 증가
        DB-->>-EC: 업데이트 완료
        
        alt 재시도 한계 미달
            EC->>+RETRY: 재시도 스케줄링<br/>Exponential Backoff<br/>(1s → 2s → 4s → 8s)
            Note right of RETRY: 재시도 로직 구현
            RETRY-->>-EC: 재시도 스케줄 완료
            EC->>KB: 오프셋 커밋 (재처리하지 않음)
        else 재시도 한계 초과
            EC->>+DB: EventLog 상태<br/>DEAD_LETTER로 업데이트
            Note right of DB: 수동 처리 필요<br/>관리자 알림 발송
            DB-->>-EC: 업데이트 완료
            EC->>KB: 오프셋 커밋
        end
    end

    Note over API,RETRY: 비동기 외부 연동<br/>재시도 및 Dead Letter 처리
```

---

## 🔧 Kafka 구성 설정

### 1. Topic 구성 상세

| Topic 명 | Partition 수 | Replication Factor | 용도 |
|----------|--------------|-------------------|---------|
| `coupon-requests` | 3 | 1 | 쿠폰 발급 요청 |
| `coupon-results` | 3 | 1 | 쿠폰 발급 결과 |
| `order-completed` | 3 | 1 | 주문 완료 이벤트 |
| `external-events` | 3 | 1 | 외부 시스템 연동 |
| `product-events` | 3 | 1 | 상품 관련 이벤트 |

### 2. Producer 설정

```yaml
# application.yml
spring:
  kafka:
    producer:
      # 성능 최적화
      acks: all                          # 모든 ISR 확인 후 응답
      retries: 3                         # 재시도 횟수
      batch-size: 16384                  # 16KB 배치 크기
      linger-ms: 10                      # 최대 10ms 대기
      buffer-memory: 33554432            # 32MB 버퍼
      compression-type: snappy           # 압축 방식
      
      # 신뢰성 보장
      enable-idempotence: true           # 중복 메시지 방지
      max-in-flight-requests-per-connection: 5
      
      # 직렬화
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
      properties:
        # 타임아웃 설정
        delivery.timeout.ms: 120000      # 2분 전체 타임아웃
        request.timeout.ms: 30000        # 30초 요청 타임아웃
        
```

### 3. Consumer 설정

```yaml
spring:
  kafka:
    consumer:
      # 성능 최적화  
      fetch-min-size: 1024               # 최소 1KB 페치
      fetch-max-wait: 500                # 최대 0.5초 대기
      max-poll-records: 500              # 한 번에 최대 500개 처리
      
      # 오프셋 관리
      enable-auto-commit: false          # 수동 커밋
      auto-offset-reset: earliest        # 처음부터 읽기
      
      # 세션 관리
      session-timeout: 10000             # 10초 세션 타임아웃
      heartbeat-interval: 3000           # 3초 하트비트
      
      # 역직렬화
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "kr.hhplus.be.server.domain.event"
        
```

### 4. Consumer Factory 구성

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    // 쿠폰 요청 전용 Consumer Factory
    @Bean
    public ConsumerFactory<String, CouponRequestEvent> couponRequestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-request-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponRequestEvent.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "kr.hhplus.be.server.domain.event");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponRequestEvent> 
            couponRequestKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CouponRequestEvent> factory
                = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(couponRequestConsumerFactory());
        
        // 동시성 설정 - 파티션 수와 동일하게 설정
        factory.setConcurrency(3);  // 3개 파티션 = 3개 스레드로 병렬 처리
        
        // 수동 ACK 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // 에러 처리
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new FixedBackOff(1000L, 3)
        ));
        
        return factory;
    }
}
```

---

## 📝 구현된 주요 기능

### 1. **이벤트 드리븐 아키텍처**
- Kafka를 통한 비동기 메시지 처리
- 도메인별 Consumer 구현 (쿠폰, 주문, 상품, 외부연동)
- EventLog를 통한 이벤트 추적

### 2. **파티셔닝 및 병렬 처리**  
- **파티션 설정**: 모든 토픽 3개 파티션으로 구성
- **컨슈머 Concurrency**: 파티션 수와 동일한 3개 스레드
- **userId 기반 파티셔닝**으로 사용자별 순서 보장
- **병렬 처리**로 처리 성능 3배 향상

### 3. **Consumer 구현**
- `CouponRequestConsumer`: 쿠폰 발급 요청 처리
- `CouponResultConsumer`: 쿠폰 발급 결과 처리  
- `OrderCompletedConsumer`: 주문 완료 후 처리
- `ProductEventConsumer`: 상품 랭킹 업데이트
- `ExternalEventConsumer`: 외부 시스템 연동

### 4. **안정성 확보**
- 수동 오프셋 커밋
- 에러 핸들링 및 재시도 로직
- Dead Letter 처리