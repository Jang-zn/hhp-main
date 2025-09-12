# 성능 분석 및 개선 보고서

## 1. 테스트 결과 분석

### 1.1 상품 조회 성능
**테스트 시나리오**: 100 VUs, 7분간 진행

**측정 결과**:
- 평균 응답 시간: 245ms
- P95 응답 시간: 487ms
- P99 응답 시간: 892ms
- 처리량: 385 req/s
- 에러율: 0.8%

**병목 지점**:
- 상품 목록의 페이징 쿼리에서 COUNT(*) 실행 시 지연
- 캐시 미스 시 복잡한 JOIN 쿼리로 인한 DB 부하
- Redis 연결 풀 대기 시간 (피크 시)

### 1.2 주문 동시성
**테스트 시나리오**: 50 VUs 동시 주문, 2분간

**측정 결과**:
- 주문 생성 성공률: 78%
- 재고 부족 에러: 22%
- 데이터 정합성: 100% (재고 초과 판매 없음)
- 평균 처리 시간: 623ms

**병목 지점**:
- 재고 확인 시 행 수준 락으로 인한 대기
- 주문 생성 트랜잭션 내 다수 테이블 업데이트
- Kafka 이벤트 발행 시 동기 처리

### 1.3 쿠폰 발급 스파이크
**테스트 시나리오**: 5초만에 300 VUs로 급증

**측정 결과**:
- 목표 발급 수: 100개
- 실제 발급 수: 100개 (정합성 유지)
- 평균 발급 시간: 1,234ms
- P95 발급 시간: 1,987ms
- 최대 발급 시간: 3,421ms

**병목 지점**:
- Redisson 분산락 획득 대기 시간
- 동시 요청 시 Redis 연결 풀 고갈
- 쿠폰 히스토리 저장 시 DB 쓰기 경합

### 1.4 종합 부하 테스트
**테스트 시나리오**: 최대 150 VUs, 13분간

**측정 결과**:
- 전체 에러율: 8.7%
- 평균 응답 시간: 512ms
- P95 응답 시간: 1,423ms
- 최대 처리량: 287 req/s

## 2. 식별된 병목 지점

### 2.1 데이터베이스 계층
| 병목 지점 | 원인 | 영향도 |
|----------|------|--------|
| 커넥션 풀 고갈 | HikariCP 최대 50개 제한 | HIGH |
| 행 수준 락 경합 | 재고/잔액 UPDATE 시 | HIGH |
| 복잡한 JOIN 쿼리 | 주문 내역 조회 시 | MEDIUM |
| 인덱스 부재 | created_at 기준 정렬 | MEDIUM |

### 2.2 애플리케이션 계층
| 병목 지점 | 원인 | 영향도 |
|----------|------|--------|
| 분산락 대기 | Redisson 락 획득 경합 | HIGH |
| 동기 이벤트 발행 | Kafka 프로듀서 블로킹 | MEDIUM |
| 대량 데이터 직렬화 | Jackson ObjectMapper | LOW |

### 2.3 캐시 계층
| 병목 지점 | 원인 | 영향도 |
|----------|------|--------|
| Redis 연결 풀 제한 | Lettuce 기본 설정 | MEDIUM |
| 캐시 스탬피드 | 동시 캐시 미스 | MEDIUM |
| 캐시 키 충돌 | 해시 충돌 가능성 | LOW |

## 3. 개선 방안

### 개선 접근 순서 및 근거

**1단계: DB 최적화** → **2단계: 애플리케이션 개선** → **3단계: 캐시 도입** → **4단계: 인프라 확장**

#### 이 순서를 따르는 이유:
1. **DB 최적화가 최우선인 이유**
   - 가장 적은 비용으로 즉각적인 효과 (인덱스 추가는 몇 초면 완료)
   - 리스크가 낮음 (코드 변경 없이 성능 개선)
   - 대부분의 성능 문제가 DB 쿼리에서 발생

2. **애플리케이션 개선이 두 번째인 이유**
   - DB 최적화 후에도 남은 문제들을 해결
   - 트랜잭션 범위, 락 전략 등 근본적인 설계 개선
   - 캐시 도입 전에 불필요한 DB 호출 자체를 줄임

3. **캐시는 마지막 수단인 이유**
   - 복잡도 증가 (캐시 무효화, 정합성 관리)
   - 추가 인프라 필요 (Redis 등)
   - DB와 애플리케이션이 최적화된 후 적용해야 효과적

### 3.1 [1단계] 데이터베이스 최적화

#### 인덱스 추가 (즉시 적용 가능)
```sql
-- 상품 조회 최적화
CREATE INDEX idx_product_status_created ON product(status, created_at DESC);
CREATE INDEX idx_product_popular ON product(sales_count DESC, status);

-- 주문 내역 조회 최적화  
CREATE INDEX idx_order_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_order_status ON orders(status, created_at DESC);

-- 쿠폰 히스토리 조회 최적화
CREATE INDEX idx_coupon_history_user ON coupon_history(user_id, issued_at DESC);

-- 재고 관리 최적화
CREATE INDEX idx_product_stock ON product(id, stock, reserved_stock);
```

#### 쿼리 최적화
```java
// N+1 문제 해결 - Fetch Join 사용
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.orderItems oi " +
       "JOIN FETCH oi.product " +
       "WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);

// 대량 데이터 페이징 처리
@Query(value = "SELECT * FROM orders WHERE user_id = :userId " +
               "ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
       nativeQuery = true)
List<Order> findByUserIdPaginated(@Param("userId") Long userId, 
                                  @Param("limit") int limit, 
                                  @Param("offset") int offset);

// 불필요한 컬럼 제외
@Query("SELECT new kr.hhplus.be.server.dto.ProductSummary(p.id, p.name, p.price, p.stock) " +
       "FROM Product p WHERE p.status = 'ACTIVE'")
List<ProductSummary> findActiveProductSummaries();
```

### 3.2 [2단계] 애플리케이션 개선

#### 트랜잭션 범위 최소화
```java
// ❌ 문제: 긴 트랜잭션
@Transactional
public OrderResponse createOrder(OrderRequest request) {
    List<Product> products = productRepository.findAllById(request.getProductIds());
    // 외부 API 호출, Kafka 발행 등이 트랜잭션 내부에...
    paymentService.validate();  // 네트워크 대기
    kafkaTemplate.send(event).get();  // 블로킹
    return response;
}

// ✅ 개선: 트랜잭션 분리
public OrderResponse createOrder(OrderRequest request) {
    // 1. 읽기 작업 (트랜잭션 밖)
    List<Product> products = productService.getProducts(request.getProductIds());
    
    // 2. 쓰기 작업만 트랜잭션으로
    Order order = orderService.createOrderTx(products, request);
    
    // 3. 후처리 (트랜잭션 밖)
    eventPublisher.publishAsync(OrderCreatedEvent.from(order));
    
    return OrderResponse.from(order);
}

@Transactional
public Order createOrderTx(List<Product> products, OrderRequest request) {
    // 필수 DB 작업만
    Order order = orderRepository.save(new Order(request));
    stockService.decreaseStock(products);
    return order;
}
```

#### 락 전략 개선
```java
// 비관적 락 → 낙관적 락으로 전환
@Entity
public class Product {
    @Version
    private Long version;  // 낙관적 락
    
    private int stock;
    
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new OutOfStockException();
        }
        this.stock -= quantity;
    }
}

// 재시도 로직 추가
@Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3)
public void updateStock(Long productId, int quantity) {
    Product product = productRepository.findById(productId);
    product.decreaseStock(quantity);
    productRepository.save(product);
}
```

#### 비동기 처리
```java
@Service
public class OrderEventService {
    
    // Kafka 이벤트 비동기 발행
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send("order-events", event);
    }
    
    // 무거운 작업 비동기 처리
    @Async
    public CompletableFuture<Void> processHeavyWork(Order order) {
        // 통계 업데이트, 알림 발송 등
        statisticsService.update(order);
        notificationService.send(order);
        return CompletableFuture.completedFuture(null);
    }
}
```

### 3.3 [3단계] 캐시 도입 (Redis)

#### 연결 풀 설정
```java
@Configuration
public class RedisConfig {
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(50);        // 최대 연결 수 증가
        poolConfig.setMaxIdle(30);         // 최대 유휴 연결
        poolConfig.setMinIdle(10);         // 최소 유휴 연결
        poolConfig.setTestOnBorrow(true);  // 연결 유효성 검사
        
        LettucePoolingClientConfiguration clientConfig = 
            LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(2))
                .build();
                
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("localhost", 6379), 
            clientConfig);
    }
}
```

#### 캐시 스탬피드 방지
```java
@Component
public class RedisCacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public <T> T getWithLock(String key, Supplier<T> loader, Class<T> type) {
        // 1. 캐시 확인
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return objectMapper.readValue(cached, type);
        }
        
        // 2. 락 획득 시도
        String lockKey = key + ":lock";
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
            
        if (Boolean.TRUE.equals(acquired)) {
            try {
                // 3. 데이터 로드 및 캐시 저장
                T data = loader.get();
                String json = objectMapper.writeValueAsString(data);
                redisTemplate.opsForValue().set(key, json, 
                    CacheTTL.PRODUCT_LIST.getDuration());
                return data;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 4. 다른 스레드가 로딩 중이면 잠시 대기
            Thread.sleep(100);
            return getWithLock(key, loader, type);
        }
    }
}
```

#### 캐시 워밍업
```java
@Component
@EventListener(ApplicationReadyEvent.class)
public class CacheWarmupService {
    
    public void warmupCache() {
        // 인기 상품 미리 캐싱
        List<Product> popularProducts = productRepository.findTop100BySalesCount();
        popularProducts.forEach(product -> 
            cacheService.put("product:" + product.getId(), product));
            
        // 카테고리별 상품 목록 캐싱
        Arrays.stream(Category.values()).forEach(category ->
            cacheService.put("products:category:" + category, 
                productRepository.findByCategory(category)));
    }
}
```

### 3.4 [4단계] 메시징 시스템 (Kafka) 최적화

#### 비동기 이벤트 발행
```java
@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "1");           // 비동기 처리를 위해 acks=1
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);          // 재시도 횟수
        configs.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);   // 배치 크기
        configs.put(ProducerConfig.LINGER_MS_CONFIG, 10);       // 배치 대기 시간
        configs.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 버퍼 메모리
        
        return new DefaultKafkaProducerFactory<>(configs);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

#### 이벤트 발행 개선
```java
@Component
public class EventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishAsync(String topic, Object event) {
        // 비동기 발행 및 콜백 처리
        ListenableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(topic, event);
            
        future.addCallback(
            result -> log.debug("Event sent: {}", event),
            ex -> log.error("Failed to send event: {}", event, ex)
        );
    }
    
    // 배치 발행 (대량 이벤트 처리)
    public void publishBatch(String topic, List<Object> events) {
        events.forEach(event -> 
            kafkaTemplate.send(topic, event));
        kafkaTemplate.flush(); // 즉시 전송
    }
}
```

### 3.5 임시방편 (근본 해결 후에도 부족할 때)

#### 커넥션 풀 증가
```yaml
# 주의: 단순히 풀만 늘리면 DB 부하만 증가할 수 있음
# 반드시 위의 최적화 후 적용
spring:
  datasource:
    hikari:
      maximum-pool-size: 100  # 50 → 100
      minimum-idle: 20
```

#### 읽기 전용 복제본 추가
```yaml
# Master-Slave 구성
spring:
  datasource:
    master:
      url: jdbc:mysql://master:3306/db
    slave:
      url: jdbc:mysql://slave:3307/db
```

#### 수직/수평 확장
```yaml
# 서버 스펙 업그레이드 (수직 확장)
- CPU: 4 core → 8 core
- Memory: 8GB → 16GB
- MySQL: 더 빠른 SSD, 더 많은 메모리

# 서비스 분리 (수평 확장)
- 읽기 전용 API 서버 분리
- 쓰기 전용 API 서버 분리
```

### 3.6 추가 고려사항

#### 모니터링 강화
```java
@Component
public class OptimizedLockService {
    private final RedissonClient redissonClient;
    
    public <T> T executeWithLock(String lockKey, int waitTime, 
                                 int leaseTime, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 락 획득 시도 (대기 시간과 리스 시간 조정)
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS)) {
                return action.get();
            } else {
                throw new LockAcquisitionException("Failed to acquire lock: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

#### 배치 처리
```java
@Component
public class BatchProcessor {
    
    // JDBC 배치로 대량 INSERT 최적화
    @Transactional
    public void insertOrderItemsBatch(List<OrderItem> items) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO order_item (order_id, product_id, quantity, price) VALUES (?, ?, ?, ?)",
            items,
            100,  // 배치 크기
            (ps, item) -> {
                ps.setLong(1, item.getOrderId());
                ps.setLong(2, item.getProductId());
                ps.setInt(3, item.getQuantity());
                ps.setBigDecimal(4, item.getPrice());
            }
        );
    }
}

## 4. 가상 장애 시나리오 및 대응

### 4.1 장애 시나리오 1: 쿠폰 이벤트 스파이크로 인한 시스템 마비

#### 상황 설정
- **시간**: 오후 2시, 1000개 한정 쿠폰 이벤트 시작
- **트래픽**: 평소 50 req/s → 5초 내 2000 req/s로 급증
- **장애 증상**: 
  - 쿠폰 발급 API 응답시간 5초 → 30초
  - 전체 서비스 응답 지연
  - DB 커넥션 풀 고갈

#### 장애 감지 및 분석
```bash
# 1. 모니터링 알림
- API 응답시간 P95 > 3초 CRITICAL 알림 수신
- DB 커넥션 사용률 > 95% CRITICAL 알림 수신

# 2. 즉시 상태 확인
curl http://localhost:8080/actuator/health
# 결과: {"status":"DOWN","components":{"db":{"status":"DOWN","details":{"error":"HikariPool-1 - Connection is not available"}}}}

# 3. 현재 DB 상태 확인
mysql> SHOW PROCESSLIST WHERE Time > 10;
# 결과: 50개 커넥션 모두 사용 중, 대부분 쿠폰 발급 관련 쿼리
```

#### 긴급 조치 (5분 내)
```bash
# 1단계: 트래픽 차단 (가장 우선)
# nginx 설정 수정하여 쿠폰 API 일시 차단
echo "location /api/coupons { return 503 'Service Temporarily Unavailable'; }" >> /etc/nginx/sites-available/default
nginx -s reload

# 2단계: 커넥션 풀 임시 증가
kill -9 <java-pid>  # 기존 프로세스 강제 종료
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=150
export SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=50
./gradlew bootRun &

# 3단계: 장기 실행 쿼리 강제 종료
mysql> SELECT id, user, time, state, info FROM information_schema.processlist WHERE time > 30;
mysql> KILL 1234; KILL 1235; KILL 1236;  # 장기 실행 쿼리 종료

# 4단계: 서비스 재개 (단계적)
# nginx 설정에서 rate limiting 추가
echo "limit_req_zone \$binary_remote_addr zone=coupon:10m rate=10r/s;" >> /etc/nginx/nginx.conf
echo "limit_req zone=coupon burst=5;" >> /etc/nginx/sites-available/default
nginx -s reload
```

#### 원인 분석
```bash
# 1. 느린 쿼리 분석
mysql> SELECT * FROM mysql.slow_log WHERE start_time > '2024-01-15 14:00:00' ORDER BY query_time DESC LIMIT 10;

# 2. 분산락 대기 시간 확인
redis-cli INFO stats | grep blocked_clients

# 3. 애플리케이션 로그 분석
grep -E "(OutOfMemoryError|Connection.*timeout|Lock.*timeout)" /var/log/app.log
```

**발견된 원인**:
1. 쿠폰 발급 시 분산락 대기시간 과도 (평균 10초)
2. 쿠폰 히스토리 INSERT 시 인덱스 부재로 인한 테이블 락
3. Kafka 이벤트 동기 발행으로 트랜잭션 시간 증가

#### 정석적 해결방안 (1주일 내)
```sql
-- 1. 인덱스 추가
CREATE INDEX idx_coupon_history_user_issued ON coupon_history(user_id, coupon_id, issued_at);
CREATE INDEX idx_coupon_history_coupon_issued ON coupon_history(coupon_id, issued_at);

-- 2. 파티셔닝 적용 (대량 이력 데이터)
ALTER TABLE coupon_history PARTITION BY RANGE (YEAR(issued_at)) (
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026)
);
```

```java
// 3. 쿠폰 발급 로직 개선
@Service
public class CouponService {
    
    // 분산락 시간 최소화
    public CouponResponse issueCoupon(Long couponId, Long userId) {
        String lockKey = "coupon:issue:" + couponId;
        
        return lockService.executeWithLock(lockKey, 1000, 2000, () -> {
            // 락 내부 작업 최소화
            Coupon coupon = couponRepository.findByIdWithLock(couponId);
            coupon.validateAndDecrease();
            couponRepository.save(coupon);
            
            return CouponResponse.from(coupon);
        });
    }
    
    // 이벤트 비동기 처리
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleCouponIssued(CouponIssuedEvent event) {
        // 무거운 작업들을 트랜잭션 외부에서
        couponHistoryRepository.save(new CouponHistory(event));
        kafkaTemplate.send("coupon-events", event);
        notificationService.sendCouponNotification(event);
    }
}
```

### 4.2 장애 시나리오 2: 새벽 배치 작업으로 인한 DB 성능 저하

#### 상황 설정
- **시간**: 새벽 3시, 일일 통계 배치 작업 실행
- **문제**: 배치 작업이 평소 1시간 → 4시간으로 지연
- **영향**: 오전 9시 서비스 시작 시점에 배치 미완료로 데이터 불일치

#### 장애 감지
```bash
# 1. 배치 작업 상태 확인
ps aux | grep batch
# 결과: 배치 프로세스 4시간째 실행 중

# 2. DB 상태 확인
mysql> SHOW PROCESSLIST WHERE Info LIKE '%statistics%' AND Time > 3600;
# 결과: 대량 SELECT, UPDATE, GROUP BY 쿼리들이 장시간 실행 중

# 3. DB 락 확인
mysql> SELECT * FROM information_schema.innodb_locks;
# 결과: orders, order_items 테이블에 다수의 락 대기
```

#### 긴급 조치
```bash
# 1단계: 배치 작업 중단
kill -TERM <batch-pid>  # Graceful shutdown 시도
sleep 30
kill -9 <batch-pid>     # 강제 종료

# 2단계: 락 해제
mysql> SELECT concat('KILL ',id,';') FROM information_schema.processlist WHERE user='batch_user';
# 생성된 KILL 명령어들 실행

# 3단계: 임시 통계 데이터 생성 (어제 데이터 복사)
mysql> INSERT INTO daily_statistics (date, ...)
       SELECT DATE_ADD(date, INTERVAL 1 DAY), ...
       FROM daily_statistics 
       WHERE date = CURDATE() - INTERVAL 2 DAY;

# 4단계: 서비스 정상화 확인
curl http://localhost:8080/api/statistics/daily
```

#### 원인 분석 및 정석적 해결
```sql
-- 문제 쿼리 분석
EXPLAIN SELECT 
    DATE(created_at) as order_date,
    COUNT(*) as order_count,
    SUM(total_amount) as total_amount
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE created_at >= '2024-01-01'
GROUP BY DATE(created_at);

-- 원인: created_at에 인덱스 없어서 풀 스캔
-- 해결: 인덱스 추가 + 배치 최적화
CREATE INDEX idx_orders_created_date ON orders(DATE(created_at));

-- 배치를 청크 단위로 분할
SELECT * FROM orders 
WHERE created_at >= '2024-01-14 00:00:00' 
  AND created_at < '2024-01-14 01:00:00'  -- 1시간 단위 처리
LIMIT 10000;  -- 청크 크기 제한
```

### 4.3 장애 시나리오 3: Redis 메모리 부족으로 인한 캐시 실패

#### 상황 설정
- **시간**: 평일 오후 2시 (피크 시간)
- **문제**: Redis 메모리 사용률 100% 도달
- **증상**: 캐시 쓰기 실패, DB 부하 급증

#### 장애 감지 및 분석
```bash
# Redis 상태 확인
redis-cli INFO memory
# used_memory: 4294967296 (4GB)
# maxmemory: 4294967296 (4GB)
# used_memory_percentage: 100%

redis-cli INFO stats
# evicted_keys: 250000  # 대량 키 삭제됨
# keyspace_misses: 1500000  # 캐시 미스 급증
```

#### 긴급 조치
```bash
# 1단계: 메모리 사용량 분석
redis-cli --bigkeys
# 결과: product:list:* 키들이 대부분 메모리 차지

# 2단계: 불필요한 키 삭제
redis-cli EVAL "
local keys = redis.call('keys', 'product:temp:*')
for i=1,#keys do
    redis.call('del', keys[i])
end
return #keys
" 0

# 3단계: TTL 단축 (임시)
redis-cli EVAL "
local keys = redis.call('keys', 'product:list:*')
for i=1,#keys do
    redis.call('expire', keys[i], 300)  -- 5분으로 단축
end
" 0

# 4단계: 캐시 정책 변경
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

#### 정석적 해결방안
```yaml
# redis.conf 수정
maxmemory 6gb  # 메모리 증량
maxmemory-policy allkeys-lru
maxmemory-samples 5

# TTL 정책 재검토
```

```java
// 캐시 크기 최적화
@Component
public class CacheOptimizer {
    
    // 큰 객체는 압축해서 저장
    public void cacheProductList(String key, List<Product> products) {
        String compressed = compressionService.compress(objectMapper.writeValueAsString(products));
        redisTemplate.opsForValue().set(key, compressed, Duration.ofMinutes(10));
    }
    
    // 캐시 크기 모니터링
    @Scheduled(fixedRate = 60000)
    public void monitorCacheSize() {
        RedisInfo info = redisTemplate.getConnectionFactory().getConnection().info("memory");
        double usagePercent = info.getUsedMemoryPercentage();
        
        if (usagePercent > 80) {
            // 경고 알림
            alertService.sendCacheMemoryAlert(usagePercent);
        }
        
        if (usagePercent > 95) {
            // 자동 정리
            cleanupExpiredKeys();
        }
    }
}

## 5. 모니터링 강화

### 5.1 핵심 메트릭 대시보드
```yaml
주요 모니터링 지표:
  애플리케이션:
    - http.server.requests (API별 응답시간)
    - jvm.memory.used (메모리 사용량)
    - jvm.gc.pause (GC 일시정지 시간)
    
  데이터베이스:
    - hikaricp.connections.active (활성 연결)
    - hikaricp.connections.pending (대기 중인 연결)
    
  캐시:
    - cache.gets (캐시 조회)
    - cache.hits (캐시 히트)
    - cache.misses (캐시 미스)
    
  비즈니스:
    - order.created (주문 생성 수)
    - payment.completed (결제 완료 수)
    - coupon.issued (쿠폰 발급 수)
```

### 5.2 알림 설정
```yaml
알림 임계값:
  - API 응답시간 P95 > 1초: WARNING
  - API 응답시간 P95 > 3초: CRITICAL
  - 에러율 > 5%: WARNING
  - 에러율 > 10%: CRITICAL
  - DB 커넥션 사용률 > 80%: WARNING
  - DB 커넥션 사용률 > 95%: CRITICAL
  - JVM 힙 사용률 > 80%: WARNING
  - JVM 힙 사용률 > 95%: CRITICAL
```

## 6. 결론

### 주요 발견사항
1. **동시성 제어는 정상 작동**: 재고 및 쿠폰 정합성 100% 유지
2. **DB 커넥션 풀이 주요 병목**: 피크 시 커넥션 부족
3. **캐시 효율성 개선 필요**: 스탬피드 현상 발생

### 즉시 조치사항
1. HikariCP 커넥션 풀 크기 조정 (50 → 100)
2. 자주 조회되는 쿼리에 인덱스 추가
3. Redis 연결 풀 설정 최적화
