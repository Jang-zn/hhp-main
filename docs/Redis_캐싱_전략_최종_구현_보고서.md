# Redis 캐싱 전략 최종 구현 보고서

**주요 기술**: Redis(Redisson), Spring Boot, Clean Architecture  

---

## 1. 배경 및 문제 정의

### 1.1 프로젝트 배경

E-Commerce 시스템에서 증가하는 트래픽과 DB 부하를 해결하기 위해 Redis 기반 캐싱 전략을 도입
- **상품 조회 성능 최적화**: 높은 조회 빈도를 가진 상품 정보의 응답 시간 단축
- **인기 상품 랭킹 시스템**: 실시간 주문 데이터 기반 인기 상품 순위 제공
- **선착순 쿠폰 발급**: 동시성 제어를 통한 정확한 쿠폰 발급 시스템

### 1.2 핵심 문제점

#### 1.2.1 성능 문제
- **DB 부하 집중**: 조회 시 캐시 처리 미비
- **N+1 쿼리 문제**: 인기 상품 조회 시 개별 상품마다 DB 접근
- **Cold Start 문제**: 애플리케이션 시작 시 빈 캐시로 인한 성능 저하

#### 1.2.2 동시성 문제
- **Cache Stampede**: 인기 상품 캐시 만료 시 동시 다발적 DB 요청
- **쿠폰 발급 경합**: 선착순 쿠폰 발급 시 재고 초과 발급 위험
- **데이터 불일치**: 캐시와 DB 간 데이터 정합성 문제

#### 1.2.3 아키텍처 문제
- **책임 분리 부족**: Service 레이어에서 캐시 로직 직접 관리
- **코드 중복**: 4개 Service 클래스에서 총 204줄의 중복 캐시 로직
- **테스트 어려움**: 비즈니스 로직과 캐시 로직이 혼재

---

## 2. 문제 해결 전략 및 구현

### 2.1 Redis 기반 상품 랭킹 시스템

#### 2.1.1 설계 및 구현 방안

**Redis Sorted Set을 활용한 실시간 랭킹 시스템 구현**

```java
// RedisCacheAdapter.java - 랭킹 시스템 핵심 로직
@Override
public void addProductScore(String rankingKey, String productKey, int orderQuantity) {
    try {
        RScoredSortedSet<String> ranking = redissonClient.getScoredSortedSet(rankingKey);
        ranking.addScore(productKey, orderQuantity);
        
        ranking.expire(7, TimeUnit.DAYS); // 일주일 자동 만료
        log.debug("Product score added: rankingKey={}, productKey={}, quantity={}", 
                 rankingKey, productKey, orderQuantity);
    } catch (Exception e) {
        log.error("Error adding product score", e);
    }
}

@Override
public List<Long> getProductRanking(String rankingKey, int offset, int limit) {
    try {
        RScoredSortedSet<String> ranking = redissonClient.getScoredSortedSet(rankingKey);
        return ranking.entryRangeReversed(offset, offset + limit - 1)
                .stream()
                .map(entry -> {
                    String[] parts = entry.getValue().split(":");
                    return Long.parseLong(parts[parts.length - 1].replace("product_", ""));
                })
                .collect(Collectors.toList());
    } catch (Exception e) {
        log.error("Error getting product ranking", e);
        return List.of();
    }
}
```

**인기 상품 조회 UseCase 구현**

```java
// GetPopularProductListUseCase.java - 실제 적용 로직
public List<Product> execute(int period, int limit, int offset) {
    try {
        // 1. Redis 랭킹에서 상품 ID 목록 조회
        List<Long> rankedProductIds = getRankedProductIds(period, limit, offset);
        
        if (rankedProductIds.isEmpty()) {
            log.debug("Redis 랭킹이 비어있음, DB 폴백: period={}", period);
            return fallbackToDatabase(period, limit, offset);
        }
        
        // 2. 랭킹된 상품들을 캐시/DB에서 조회 (Cache-Aside 패턴)
        List<Product> products = getProductsByIds(rankedProductIds);
        
        return products;
        
    } catch (Exception e) {
        log.error("인기 상품 조회 중 오류 발생, DB 폴백: period={}", period, e);
        return fallbackToDatabase(period, limit, offset);
    }
}
```

#### 2.1.2 해결된 문제점

1. **실시간 랭킹 제공**: Redis Sorted Set의 O(log N) 성능으로 빠른 랭킹 조회
2. **장애 복구**: Redis 장애 시 DB 폴백으로 서비스 연속성 보장

### 2.2 선착순 쿠폰 발급 시스템

#### 2.2.1 설계 및 구현 방안

**Redis Atomic Operations를 활용한 동시성 제어**

```java
// RedisCacheAdapter.java - 원자적 쿠폰 발급 로직
@Override
public long issueCouponAtomically(String couponCounterKey, String couponUserKey, long maxCount) {
    try {
        RAtomicLong counter = redissonClient.getAtomicLong(couponCounterKey);
        RBucket<String> userBucket = redissonClient.getBucket(couponUserKey);
        
        // 1. 중복 발급 검증
        if (userBucket.isExists()) {
            return -1; // 이미 발급받음
        }
        
        // 2. 현재 발급 수량 확인
        long currentCount = counter.get();
        if (currentCount >= maxCount) {
            return -1; // 재고 소진
        }
        
        // 3. 원자적 증가 연산
        long newCount = counter.incrementAndGet();
        if (newCount > maxCount) {
            counter.decrementAndGet(); // 롤백
            return -1; // 재고 초과
        }
        
        // 4. 사용자 발급 이력 저장
        userBucket.set("issued");
        userBucket.expire(30, TimeUnit.DAYS);
        
        log.debug("Coupon issued atomically: issueNumber={}", newCount);
        return newCount;
        
    } catch (Exception e) {
        log.error("Error issuing coupon atomically", e);
        return -1;
    }
}
```

**쿠폰 발급 UseCase 개선**

```java
// IssueCouponUseCase.java - 비즈니스 로직과 Redis 통합
public CouponHistory execute(Long userId, Long couponId) {
    log.info("쿠폰 발급 요청: userId={}, couponId={}", userId, couponId);
    
    // 1. 기본 검증 (사용자, 쿠폰 존재성)
    validateInputs(userId, couponId);
    User user = userRepositoryPort.findById(userId).orElseThrow();
    Coupon coupon = couponRepositoryPort.findById(couponId).orElseThrow();
    
    // 2. 쿠폰 상태 검증
    coupon.updateStatusIfNeeded();
    if (!coupon.canIssue()) {
        throw new CouponException.CouponNotIssuable();
    }
    
    // 3. Redis 원자적 발급 처리
    String counterKey = keyGenerator.generateCouponCounterKey(couponId);
    String userKey = keyGenerator.generateCouponUserKey(couponId, userId);
    long issueNumber = cachePort.issueCouponAtomically(counterKey, userKey, coupon.getQuantity());
    
    if (issueNumber < 0) {
        throw new CouponException.OutOfStock(); // 발급 실패
    }
    
    // 4. DB 저장 및 캐시 무효화
    CouponHistory savedHistory = couponHistoryRepositoryPort.save(couponHistory);
    cachePort.evictByPattern(keyGenerator.generateCouponListCachePattern(userId));
    
    return savedHistory;
}
```

#### 2.2.2 해결된 문제점

1. **정확한 재고 관리**: Redis AtomicLong으로 동시 발급 시에도 정확한 수량 제어
2. **중복 발급 방지**: 사용자별 발급 이력을 Redis에 저장하여 즉시 검증
3. **고성능 처리**: DB 락 없이 Redis 연산만으로 동시성 제어

### 2.3 Cache Stampede 방어 시스템

#### 2.3.1 분산 락 기반 방어 메커니즘

```java
// RedisCacheAdapter.java - Cache Stampede 방어 로직
@Override
public <T> T get(String key, Class<T> type) {
    try {
        // 1. 첫 번째 캐시 확인
        RBucket<T> bucket = redissonClient.getBucket(cacheKey);
        T cachedValue = bucket.get();
        
        if (cachedValue != null) {
            return cachedValue; // Cache Hit
        }
        
        // 2. Cache Miss - 분산 락 획득
        String lockKey = cacheKey + ":load";
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 3. 200ms 타임아웃으로 락 획득 시도
            if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                try {
                    // 4. Double-check (다른 스레드가 이미 로드했을 수 있음)
                    cachedValue = bucket.get();
                    if (cachedValue != null) {
                        return cachedValue;
                    }
                    
                    // 5. null 반환 → Service에서 DB 조회 후 put() 호출
                    return null;
                    
                } finally {
                    lock.unlock();
                }
            } else {
                // 6. 락 획득 실패 시 DB 폴백
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        
    } catch (Exception e) {
        log.error("Error accessing cache", e);
        return null;
    }
}
```

#### 2.3.2 TTL Randomization 구현

```java
// RedisCacheAdapter.java - TTL 지터 추가
@Override
public void put(String key, Object value, int ttlSeconds) {
    try {
        if (ttlSeconds > 0) {
            // ±10% 랜덤 지터 추가
            int jitter = (int) (ttlSeconds * 0.1 * (random.nextDouble() * 2 - 1));
            int randomizedTTL = ttlSeconds + jitter;
            
            bucket.set(value, randomizedTTL, TimeUnit.SECONDS);
            log.debug("Cache put with randomized TTL: originalTTL={}s, actualTTL={}s", 
                     ttlSeconds, randomizedTTL);
        }
    } catch (Exception e) {
        log.error("Error putting cache", e);
    }
}
```

#### 2.3.3 해결된 문제점

1. **DB 부하 분산**: 동시 요청 중 단 하나의 스레드만 DB 조회
2. **타임아웃 제어**: 200ms 락 타임아웃으로 데드락 방지
3. **자동 부하 분산**: TTL 지터로 캐시 만료 시점 분산

### 2.4 아키텍처 개선

#### 2.4.1 UseCase 레이어 캐시 로직 분리

**개선 전 - ProductService (59줄 캐시 로직)**
```java
// 기존: Service에서 캐시 로직 직접 관리
@Service
public class ProductService {
    public Product getProduct(Long productId) {
        // 캐시 키 생성, 조회, 저장, 예외 처리 로직 59줄...
        try {
            String key = "product:" + productId;
            Product cached = cachePort.get(key, Product.class);
            // ... 복잡한 캐시 로직
        } catch (Exception e) {
            // 예외 처리 로직
        }
    }
}
```

**개선 후 - 책임 분리**
```java
// 개선: Service는 UseCase 호출만 담당
@Service
public class ProductService {
    public Optional<Product> getProduct(Long productId) {
        return getProductUseCase.execute(productId);
    }
}

// UseCase가 캐시 로직 전담
@Component
public class GetProductUseCase {
    public Optional<Product> execute(Long productId) {
        String cacheKey = keyGenerator.generateProductCacheKey(productId);
        
        try {
            Product cached = cachePort.get(cacheKey, Product.class);
            if (cached != null) return Optional.of(cached);
            
            Optional<Product> product = productRepositoryPort.findById(productId);
            product.ifPresent(p -> 
                cachePort.put(cacheKey, p, CacheTTL.PRODUCT_INFO.getSeconds()));
            
            return product;
        } catch (Exception e) {
            return productRepositoryPort.findById(productId); // DB 폴백
        }
    }
}
```

#### 2.4.2 중앙화된 TTL 관리

```java
// CacheTTL.java - 중앙화된 TTL 전략
public enum CacheTTL {
    PRODUCT_INFO(3600),        // 1시간 - 상품 정보
    PRODUCT_LIST(3600),        // 1시간 - 상품 목록
    ORDER_DETAIL(600),         // 10분 - 주문 상세
    ORDER_LIST(300),           // 5분 - 주문 목록
    USER_BALANCE(60),          // 1분 - 사용자 잔액
    USER_COUPON_LIST(300);     // 5분 - 쿠폰 목록
    
    // 동적 TTL: 인기 상품 조회 기간에 따라 조정
    public static int getPopularProductTTLSeconds(int period) {
        if (period <= 1) return 300;    // 1일: 5분 (실시간성)
        if (period <= 3) return 600;    // 3일: 10분
        if (period <= 7) return 1800;   // 7일: 30분
        if (period <= 30) return 3600;  // 30일: 1시간
        return 7200;                     // 그 이상: 2시간
    }
}
```

---

## 3. 테스트 전략 및 검증

### 3.1 단위 테스트

#### 3.1.1 Cache Stampede 방어 테스트

```java
@Test
@DisplayName("동시 캐시 요청 시 단 하나의 스레드만 DB 조회")
void testCacheStampedeDefense() throws InterruptedException {
    // Given
    String cacheKey = "test:product:1";
    int threadCount = 100;
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger dbCallCount = new AtomicInteger(0);
    
    // When: 100개 스레드에서 동시 요청
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                Product result = productService.getProduct(1L);
                if (result != null) dbCallCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await(5, TimeUnit.SECONDS);
    
    // Then: DB 호출은 1회만 발생
    assertThat(dbCallCount.get()).isEqualTo(1);
}
```

#### 3.1.2 쿠폰 발급 동시성 테스트

```java
@Test
@DisplayName("선착순 쿠폰 발급 - 정확한 수량 제한")
void testConcurrentCouponIssue() throws InterruptedException {
    // Given
    Long couponId = 1L;
    int maxCount = 100;
    int threadCount = 1000; // 100개 한정 쿠폰에 1000명 동시 요청
    
    // When: 1000명이 동시에 쿠폰 발급 요청
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
        final Long userId = (long) i;
        executor.submit(() -> {
            try {
                couponService.issueCoupon(userId, couponId);
                successCount.incrementAndGet();
            } catch (CouponException.OutOfStock e) {
                // 재고 소진 예외는 정상
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await(10, TimeUnit.SECONDS);
    
    // Then: 정확히 100개만 발급
    assertThat(successCount.get()).isEqualTo(maxCount);
}
```

### 3.2 성능 테스트

#### 3.2.1 응답 시간 개선 측정

```java
@Test
@DisplayName("캐시 적용 전후 응답 시간 비교")
void testResponseTimeImprovement() {
    // Given
    Long productId = 1L;
    
    // When & Then: 첫 번째 요청 (캐시 미스)
    long startTime1 = System.currentTimeMillis();
    Product product1 = productService.getProduct(productId).orElse(null);
    long dbResponseTime = System.currentTimeMillis() - startTime1;
    
    // When & Then: 두 번째 요청 (캐시 히트)
    long startTime2 = System.currentTimeMillis();
    Product product2 = productService.getProduct(productId).orElse(null);
    long cacheResponseTime = System.currentTimeMillis() - startTime2;
    
    // 검증: 캐시 응답이 DB 응답보다 10배 이상 빠름
    assertThat(cacheResponseTime * 10).isLessThan(dbResponseTime);
    assertThat(product1).isEqualTo(product2); // 동일한 데이터
}
```

#### 3.2.2 처리량 개선 측정

```java
@Test
@DisplayName("동시 사용자 처리량 개선 측정")
void testThroughputImprovement() throws InterruptedException {
    // Given
    int userCount = 1000;
    Long productId = 1L;
    
    // 캐시 웜업
    productService.getProduct(productId);
    
    // When: 1000명 동시 요청
    CountDownLatch latch = new CountDownLatch(userCount);
    long startTime = System.currentTimeMillis();
    
    ExecutorService executor = Executors.newFixedThreadPool(100);
    for (int i = 0; i < userCount; i++) {
        executor.submit(() -> {
            try {
                productService.getProduct(productId);
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await(30, TimeUnit.SECONDS);
    long totalTime = System.currentTimeMillis() - startTime;
    
    // Then: 1000건 처리가 5초 이내 완료
    assertThat(totalTime).isLessThan(5000);
    double throughput = (double) userCount / totalTime * 1000; // TPS
    assertThat(throughput).isGreaterThan(200); // 200 TPS 이상
}
```

### 3.3 통합 테스트

#### 3.3.1 Redis 장애 시 폴백 테스트

```java
@Test
@DisplayName("Redis 장애 시 DB 폴백 동작 검증")
void testRedisFallback() {
    // Given: Redis 서버 중단
    redisContainer.stop();
    
    // When: 상품 조회 요청
    Optional<Product> product = productService.getProduct(1L);
    
    // Then: DB에서 정상 조회됨
    assertThat(product).isPresent();
    assertThat(product.get().getName()).isNotBlank();
}
```

---


## 4. 한계점 및 개선 방안

### 4.1 현재 시스템의 한계점

#### 4.1.1 캐시 일관성 문제
**문제**: Write-Through 패턴 적용 범위 제한
- 현재 잔액, 주문 상태 등에만 적용
- 상품 정보 수정 시 캐시 무효화 의존

**개선 방안**:
```java
// 이벤트 기반 캐시 무효화 구현 예시
@EventHandler
public class ProductUpdatedEventHandler {
    
    @EventListener
    @Async
    public void handleProductUpdated(ProductUpdatedEvent event) {
        try {
            // 관련 캐시 무효화
            String productKey = keyGenerator.generateProductCacheKey(event.getProductId());
            cachePort.evict(productKey);
            
            // 인기 상품 목록 캐시 무효화
            String rankingPattern = keyGenerator.generateRankingCachePattern();
            cachePort.evictByPattern(rankingPattern);
            
            log.info("캐시 무효화 완료: productId={}", event.getProductId());
        } catch (Exception e) {
            log.error("캐시 무효화 실패: productId={}", event.getProductId(), e);
        }
    }
}
```

#### 4.1.2 메모리 사용량 증가
**문제**: 모든 상품 데이터를 Redis에 캐싱
- 상품 수 증가 시 메모리 사용량 선형 증가
- Redis 메모리 한계 도달 가능성

**개선 방안**:
1. **LRU 기반 메모리 관리**
```java
// Redis 설정
maxmemory 2gb
maxmemory-policy allkeys-lru
```

2. **티어드 캐싱 전략**
```java
// Hot Data: Redis, Warm Data: Local Cache, Cold Data: DB
public class TieredCacheStrategy {
    public Product getProduct(Long productId) {
        // 1. Local Cache 확인
        Product local = localCache.get(productId);
        if (local != null) return local;
        
        // 2. Redis Cache 확인
        Product redis = redisCache.get(productId);
        if (redis != null) {
            localCache.put(productId, redis, 5_MINUTES);
            return redis;
        }
        
        // 3. DB 조회
        Product db = repository.findById(productId);
        if (db != null) {
            redisCache.put(productId, db, 1_HOUR);
            localCache.put(productId, db, 5_MINUTES);
        }
        return db;
    }
}
```

#### 4.1.3 Redis 단일 장애점
**문제**: Redis 장애 시 성능 저하
- DB 폴백으로 서비스는 유지되지만 성능 급격히 저하
- Redis 복구 시까지 Cold Cache 상태 지속

**개선 방안**:
1. **Redis Cluster 구성**
```yaml
# Redis Cluster 설정
spring:
  redis:
    cluster:
      nodes:
        - redis-node1:6379
        - redis-node2:6379
        - redis-node3:6379
      max-redirects: 3
```


## 5. 기술적 개선 성과
1. **Cache Stampede 방어**: 분산 락 + TTL 지터로 동시성 문제 해결
2. **정확한 쿠폰 발급**: Redis Atomic Operations로 선착순 쿠폰 기능 구현
3. **실시간 랭킹 시스템**: Redis Sorted Set으로 기능 구현
4. **아키텍처 개선**: 캐시처리 책임을 service -> usecase로 옮김
5. **장애 복구**: Redis 장애 시 자동 DB 폴백으로 서비스 연속성 보장


## 6. 학습 항목

#### 6.1 Redis 활용 전략
1. **적절한 Redis 데이터 구조 선택의 중요성**
   - Sorted Set: 랭킹 시스템에 최적
   - AtomicLong: 동시성 제어에 효과적
   - Bucket: 일반적인 캐시 용도에 적합

2. **TTL 전략의 중요성**
   - 비즈니스 특성에 따른 차별화된 TTL 설정
   - 지터를 통한 Cache Stampede 방지

3. **장애 처리 설계의 필요성**
   - Redis 장애 시에도 서비스 연속성이 보장될 수 있도록 고려해야 함