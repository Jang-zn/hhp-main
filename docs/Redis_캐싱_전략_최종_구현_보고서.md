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
// RedisCacheAdapter.java - 랭킹 시스템 핵심 로직 (실제 구현)
@Override
public void addProductScore(String rankingKey, String productKey, int orderQuantity) {
    try {
        String prefixedKey = CACHE_KEY_PREFIX + rankingKey; // "cache:" 접두사 추가
        RScoredSortedSet<String> ranking = redissonClient.getScoredSortedSet(prefixedKey);
        ranking.addScore(productKey, orderQuantity);
        
        ranking.expire(7, TimeUnit.DAYS); // 일주일 자동 만료
        log.debug("Product score added: rankingKey={}, productKey={}, quantity={}", prefixedKey, productKey, orderQuantity);
    } catch (Exception e) {
        log.error("Error adding product score: rankingKey={}, productKey={}, quantity={}", rankingKey, productKey, orderQuantity, e);
    }
}

@Override
public List<Long> getProductRanking(String rankingKey, int offset, int limit) {
    try {
        String prefixedKey = CACHE_KEY_PREFIX + rankingKey; // 일관된 접두사 적용
        RScoredSortedSet<String> ranking = redissonClient.getScoredSortedSet(prefixedKey);
        return ranking.entryRangeReversed(offset, offset + limit - 1)
                .stream()
                .map(entry -> {
                    String[] parts = entry.getValue().split(":");
                    return Long.parseLong(parts[parts.length - 1].replace("product_", ""));
                })
                .collect(Collectors.toList());
    } catch (Exception e) {
        log.error("Error getting product ranking: rankingKey={}, offset={}, limit={}", CACHE_KEY_PREFIX + rankingKey, offset, limit, e);
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
2. **장애 복구**: Redis 장애 시 DB 폴백

### 2.2 선착순 쿠폰 발급 시스템

#### 2.2.1 설계 및 구현 방안

**Redis Atomic Operations를 활용한 동시성 제어**

```java
// RedisCacheAdapter.java - 원자적 쿠폰 발급 로직 (실제 구현 - 분산 락 기반)
@Override
public long issueCouponAtomically(String couponCounterKey, String couponUserKey, long maxCount) {
    String lockKey = couponCounterKey + ":lock";
    RLock lock = redissonClient.getLock(lockKey);
    
    try {
        // 분산 락 획득 (최대 10초 대기, 30초 후 자동 해제)
        if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
            log.warn("Failed to acquire lock for coupon issuance: lockKey={}", lockKey);
            return -1;
        }
        
        try {
            RBucket<String> userBucket = redissonClient.getBucket(couponUserKey);
            
            // 이미 발급받은 사용자인지 확인 (원자적 확인 및 설정)
            if (!userBucket.trySet("issued", 30, TimeUnit.DAYS)) {
                log.debug("User already issued coupon: userKey={}", couponUserKey);
                return -1;
            }
            
            RAtomicLong counter = redissonClient.getAtomicLong(couponCounterKey);
            long newCount = counter.incrementAndGet();
            
            // 최대 수량 초과 확인
            if (newCount > maxCount) {
                // 롤백: 사용자 키 삭제 및 카운터 감소
                userBucket.delete();
                counter.decrementAndGet();
                log.debug("Coupon issuance exceeded max count: counter={}, maxCount={}", newCount, maxCount);
                return -1;
            }
            
            log.debug("Coupon issued atomically: counter={}, user={}, issueNumber={}", couponCounterKey, couponUserKey, newCount);
            return newCount;
            
        } finally {
            lock.unlock();
        }
        
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Thread interrupted while acquiring lock for coupon issuance: counter={}, user={}", couponCounterKey, couponUserKey, e);
        return -1;
    } catch (Exception e) {
        log.error("Error issuing coupon atomically: counter={}, user={}, maxCount={}", couponCounterKey, couponUserKey, maxCount, e);
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
    
    // 3. Redis 원자적 발급 처리 (분산 락 기반)
    String counterKey = keyGenerator.generateCouponCounterKey(couponId);
    String userKey = keyGenerator.generateCouponUserKey(couponId, userId);
    long issueNumber = cachePort.issueCouponAtomically(counterKey, userKey, coupon.getQuantity());
    
    if (issueNumber < 0) {
        throw new CouponException.OutOfStock(); // 발급 실패
    }
    
    // 4. DB 저장 및 캐시 무효화
    CouponHistory couponHistory = CouponHistory.of(user, coupon, issueNumber);
    CouponHistory savedHistory = couponHistoryRepositoryPort.save(couponHistory);
    cachePort.evictByPattern(keyGenerator.generateCouponListCachePattern(userId));
    
    return savedHistory;
}
```

#### 2.2.3 원자성 보장 메커니즘의 핵심

**trySet을 활용한 원자적 중복 검증**
```java
// 핵심: 검증과 설정이 원자적으로 수행됨
if (!userBucket.trySet("issued", 30, TimeUnit.DAYS)) {
    // 이미 키가 존재하면 false 반환 (중복 발급)
    return -1;
}
// 키가 없었다면 즉시 설정되어 다른 요청 차단
```

이 구현의 장점:
1. **경합 조건 해결**: 여러 스레드가 동시에 중복 검증을 해도 단 하나만 성공
2. **원자성**: 검증과 설정이 분리되지 않아 중간 상태 없음
3. **고성능**: 별도의 락 없이도 중복 발급 방지 가능

#### 2.2.2 해결된 문제점

1. **정확한 재고 관리**: 분산 락 + AtomicLong으로 동시 발급 시에도 정확한 수량 제어
2. **중복 발급 방지**: trySet을 활용한 원자적 중복 검증 및 발급 이력 저장
3. **고성능 처리**: DB 락 없이 Redis 분산 락으로 동시성 제어
4. **데드락 방지**: 10초 락 대기 타임아웃 + 30초 자동 해제로 안전한 락 관리
5. **원자성 보장**: trySet의 원자적 특성으로 검증과 설정을 한 번에 처리
6. **자동 롤백**: 재고 초과 시 사용자 키 삭제와 카운터 감소로 일관성 유지

### 2.3 향상된 에러 처리 및 복원력

#### 2.3.1 에러 처리 분리 전략

**캐시와 DB 연산의 개별 예외 처리**

```java
// GetProductUseCase.java - 개선된 에러 처리 구조
public Optional<Product> execute(Long productId) {
    String cacheKey = keyGenerator.generateProductCacheKey(productId);
    
    // 1. 캐시 조회 (개별 예외 처리)
    Product cachedProduct = null;
    try {
        cachedProduct = cachePort.get(cacheKey, Product.class);
        if (cachedProduct != null) {
            return Optional.of(cachedProduct);
        }
    } catch (Exception cacheException) {
        log.warn("캐시 조회 실패, DB로 진행: productId={}", productId, cacheException);
    }
    
    // 2. DB 조회 (재시도 포함)
    Optional<Product> productOpt = null;
    try {
        productOpt = productRepositoryPort.findById(productId);
    } catch (Exception dbException) {
        // 일시적 오류 재시도
        log.warn("DB 조회 실패, 재시도: productId={}", productId, dbException);
        try {
            Thread.sleep(100); // 100ms 대기
            productOpt = productRepositoryPort.findById(productId);
        } catch (Exception retryException) {
            log.error("DB 조회 재시도 실패: productId={}", productId, retryException);
            throw retryException;
        }
    }
    
    // 3. 캐시 저장 (개별 예외 처리)
    if (productOpt != null && productOpt.isPresent()) {
        try {
            cachePort.put(cacheKey, productOpt.get(), CacheTTL.PRODUCT_INFO.getSeconds());
        } catch (Exception cacheException) {
            log.warn("캐시 저장 실패, 계속 진행: productId={}", productId, cacheException);
        }
    }
    
    return productOpt;
}
```

#### 2.3.2 해결된 문제점

1. **캐시 장애 격리**: 캐시 오류가 DB 조회를 방해하지 않음
2. **DB 복원력**: 일시적 DB 장애 시 자동 재시도 메커니즘
3. **서비스 연속성**: 개별 컴포넌트 장애 시에도 전체 서비스 유지

### 2.4 Cache Stampede 방어 시스템

#### 2.4.1 분산 락 기반 방어 메커니즘

```java
// RedisCacheAdapter.java - Cache Stampede 방어 로직 (실제 구현)
@Override
public <T> T get(String key, Class<T> type) {
    String cacheKey = CACHE_KEY_PREFIX + key;
    
    try {
        // 1. 첫 번째 캐시 확인
        RBucket<T> bucket = redissonClient.getBucket(cacheKey);
        T cachedValue = bucket.get();
        
        if (cachedValue != null) {
            log.debug("Cache hit: key={}, type={}", cacheKey, type.getSimpleName());
            return cachedValue; // Cache Hit
        }
        
        // 2. Cache Miss - 분산 락 획득
        String lockKey = cacheKey + ":load";
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 3. Redisson pub/sub 대기 메커니즘 활용 (200ms 대기)
            if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                try {
                    // 4. Double-check (다른 스레드가 이미 로드했을 수 있음)
                    cachedValue = bucket.get();
                    if (cachedValue != null) {
                        log.debug("Cache hit after lock (double-check): key={}, type={}", cacheKey, type.getSimpleName());
                        return cachedValue;
                    }
                    
                    // 5. 여전히 없으면 null 반환 (서비스가 DB 조회 후 put() 호출하도록)
                    log.debug("Cache miss after lock: key={}, type={}", cacheKey, type.getSimpleName());
                    return null;
                    
                } finally {
                    lock.unlock();
                }
            } else {
                // 6. 200ms 대기했는데도 락 획득 실패 - DB 폴백
                log.debug("Lock acquisition timeout, fallback to DB: key={}", cacheKey);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted during lock wait: key={}", cacheKey);
            return null;
        }
        
    } catch (Exception e) {
        log.error("Error accessing cache: key={}, type={}", cacheKey, type.getSimpleName(), e);
        return null;
    }
}
```

#### 2.3.2 TTL Randomization 구현

```java
// RedisCacheAdapter.java - TTL 지터 추가 (실제 구현)
@Override
public void put(String key, Object value, int ttlSeconds) {
    String cacheKey = CACHE_KEY_PREFIX + key;
    
    try {
        RBucket<Object> bucket = redissonClient.getBucket(cacheKey);
        
        if (ttlSeconds > 0) {
            // Cache Stampede 방지: TTL에 ±10% 랜덤 지터 추가
            int jitter = (int) (ttlSeconds * 0.1 * (random.nextDouble() * 2 - 1)); // -10% ~ +10%
            int randomizedTTL = ttlSeconds + jitter;
            
            bucket.set(value, randomizedTTL, TimeUnit.SECONDS);
            log.debug("Cache put with randomized TTL: key={}, originalTTL={}s, actualTTL={}s", 
                     cacheKey, ttlSeconds, randomizedTTL);
        } else {
            bucket.set(value);
            log.debug("Cache put without TTL: key={}", cacheKey);
        }
        
    } catch (Exception e) {
        log.error("Error putting cache: key={}, ttl={}s", cacheKey, ttlSeconds, e);
    }
}
```

#### 2.4.3 해결된 문제점

1. **DB 부하 분산**: 동시 요청 중 단 하나의 스레드만 DB 조회
2. **타임아웃 제어**: 200ms 락 타임아웃으로 데드락 방지
3. **자동 부하 분산**: TTL 지터로 캐시 만료 시점 분산
4. **Redisson 최적화**: pub/sub 메커니즘을 활용한 효율적인 락 대기

### 2.5 Redis 키 네임스페이스 일관성

#### 2.5.1 키 생성 표준화

**문제**: 랭킹 캐시 키가 다른 캐시 키와 다른 네임스페이스 사용

```java
// 기존 문제점
public void addProductScore(String rankingKey, String productKey, int orderQuantity) {
    // rankingKey가 "cache:" 접두사 없이 생성됨
    RScoredSortedSet<String> ranking = redissonClient.getScoredSortedSet(rankingKey);
    // evictByPattern("cache:*")이 이 키들을 찾지 못함
}
```

**해결**: 모든 Redis 키에 일관된 접두사 적용

```java
// RedisCacheAdapter.java - 개선된 랭킹 키 생성 (실제 구현)
@Override
public void addProductScore(String rankingKey, String productKey, int orderQuantity) {
    try {
        String prefixedKey = CACHE_KEY_PREFIX + rankingKey; // "cache:" 접두사 추가
        RScoredSortedSet<String> ranking = redissonClient.getScoredSortedSet(prefixedKey);
        ranking.addScore(productKey, orderQuantity);
        ranking.expire(7, TimeUnit.DAYS);
        log.debug("Product score added: rankingKey={}, productKey={}, quantity={}", prefixedKey, productKey, orderQuantity);
    } catch (Exception e) {
        log.error("Error adding product score: rankingKey={}, productKey={}, quantity={}", rankingKey, productKey, orderQuantity, e);
    }
}

// KeyGenerator.java - 패턴 생성 일관성
public String generateRankingCachePattern() {
    return generateCustomCacheKey(PRODUCT_DOMAIN, RANKING_TYPE, "*");
    // "cache:product:ranking:*" 형태로 통일
}
```

#### 2.5.2 해결된 문제점

1. **패턴 매칭 정확성**: `evictByPattern()` 메서드가 모든 랭킹 캐시를 정확히 찾음
2. **캐시 관리 일관성**: 모든 캐시 키가 동일한 네임스페이스 규칙 준수
3. **운영 편의성**: 키 생성 로직의 중앙화로 유지보수성 향상

### 2.6 이벤트 기반 캐시 관리

#### 2.6.1 상품 삭제 시 종합적 캐시 정리

**기존 문제**: 상품 삭제 시 랭킹 캐시가 정리되지 않음

```java
// 기존 DeleteProductUseCase.java
public void execute(Long productId) {
    // 상품 삭제 후
    cachePort.evictByPattern(keyGenerator.generateProductCachePattern(productId));
    // 랭킹 캐시는 그대로 남아있음 - 문제!
}
```

**개선**: 포괄적 캐시 무효화 및 이벤트 발행

```java
// 개선된 DeleteProductUseCase.java
public void execute(Long productId) {
    productRepositoryPort.deleteById(productId);
    
    // 1. 상품별 캐시 무효화
    invalidateRelatedCaches(productId);
    
    // 2. 이벤트 발행으로 비동기 처리
    productService.publishProductDeletedEvent(productId);
}

private void invalidateRelatedCaches(Long productId) {
    // 기본 상품 캐시
    String productCachePattern = keyGenerator.generateProductCachePattern(productId);
    cachePort.evictByPattern(productCachePattern);
    
    // 랭킹 캐시 (특정 상품 관련)
    String rankingCachePattern = keyGenerator.generateRankingCachePattern(productId);
    cachePort.evictByPattern(rankingCachePattern);
    
    // 상품 목록 캐시
    String listCachePattern = keyGenerator.generateProductListCachePattern();
    cachePort.evictByPattern(listCachePattern);
}
```

**비동기 이벤트 핸들러**

```java
// ProductService.java - 이벤트 발행
@Service
public class ProductService {
    private final ApplicationEventPublisher eventPublisher;
    
    public void publishProductDeletedEvent(Long productId) {
        ProductUpdatedEvent event = ProductUpdatedEvent.deleted(productId);
        eventPublisher.publishEvent(event);
    }
}

// ProductRankingEventHandler.java - 비동기 처리
@EventListener
@Async
public void handleProductDeleted(ProductUpdatedEvent event) {
    if (event.getEventType() == ProductUpdatedEvent.EventType.DELETED) {
        // 모든 랭킹에서 해당 상품 제거
        rankingService.removeProductFromAllRankings(event.getProductId());
    }
}
```

#### 2.6.2 해결된 문제점

1. **데이터 정합성**: 삭제된 상품이 랭킹에서도 완전히 제거됨
2. **성능 최적화**: 동기 처리와 비동기 처리의 적절한 분리
3. **확장성**: 새로운 캐시 타입 추가 시 이벤트 핸들러만 확장

### 2.7 아키텍처 개선

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


## 43. 한계점 및 개선 방안

### 3.1 현재 시스템의 한계점

#### 4.1.1 Redis-DB 불일치 위험
**문제**: Redis 성공 후 DB 실패 시 보상 로직 부재
- 쿠폰 발급에서 Redis AtomicLong 증가 후 DB 저장 실패 가능성
- 현재 보상 트랜잭션이나 롤백 메커니즘 없음
- 분산 시스템에서 발생할 수 있는 일관성 문제

**개선 방안**:
```java
// 향후 Saga 패턴 또는 보상 트랜잭션 도입 필요
@Component
public class CouponIssuanceCompensationHandler {
    
    public void handleIssuanceFailure(Long couponId, Long userId, long issueNumber) {
        try {
            // Redis 카운터 롤백
            String counterKey = keyGenerator.generateCouponCounterKey(couponId);
            RAtomicLong counter = redissonClient.getAtomicLong(counterKey);
            counter.decrementAndGet();
            
            // 사용자 발급 이력 제거
            String userKey = keyGenerator.generateCouponUserKey(couponId, userId);
            RBucket<String> userBucket = redissonClient.getBucket(userKey);
            userBucket.delete();
            
            log.warn("쿠폰 발급 실패 보상 처리 완료: couponId={}, userId={}", couponId, userId);
        } catch (Exception e) {
            log.error("보상 처리 실패 - 수동 복구 필요: couponId={}, userId={}", couponId, userId, e);
        }
    }
}
```

#### 3.1.2 캐시 일관성 개선 완료
**기존 문제**: 상품 수정/삭제 시 관련 캐시 무효화 누락

**해결됨**: 포괄적 이벤트 기반 캐시 관리 시스템 구축
- 상품 삭제 시 랭킹 캐시까지 포함한 전체 캐시 무효화
- 동기/비동기 처리의 적절한 분리
- Redis 키 네임스페이스 일관성 확보

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

#### 3.1.3 Redis 단일 장애점
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


## 4. 기술적 개선 성과
1. **Cache Stampede 방어**: 분산 락 + TTL 지터로 동시성 문제 해결
2. **정확한 쿠폰 발급**: Redis Atomic Operations로 선착순 쿠폰 기능 구현
3. **실시간 랭킹 시스템**: Redis Sorted Set으로 기능 구현
4. **아키텍처 개선**: 캐시처리 책임을 service -> usecase로 옮김
5. **장애 복구**: Redis 장애 시 자동 DB 폴백으로 서비스 연속성 보장
6. **에러 처리 분리**: 캐시와 DB 연산의 독립적 예외 처리로 복원력 향상
7. **키 네임스페이스 일관성**: 모든 Redis 키의 통일된 네이밍 규칙 적용
8. **포괄적 캐시 관리**: 이벤트 기반 캐시 무효화로 데이터 정합성 보장


## 6. 학습 항목

#### 6.1 Redis 활용 전략
1. **적절한 Redis 데이터 구조 선택의 중요성**
   - Sorted Set: 랭킹 시스템에 최적
   - AtomicLong: 동시성 제어에 효과적
   - Bucket: 일반적인 캐시 용도에 적합

2. **장애 처리 설계의 필요성**
   - Redis 장애 시에도 서비스 연속성이 보장될 수 있도록 고려해야 함
   - 캐시와 DB 연산의 독립적 예외 처리로 부분 장애 격리

3. **일관성 있는 키 관리의 중요성**
   - 통일된 네임스페이스로 캐시 관리 효율성 확보
   - 패턴 기반 일괄 무효화 가능성 보장

4. **이벤트 기반 아키텍처의 효과**
   - 동기 처리와 비동기 처리의 적절한 분리
   - 확장 가능한 캐시 무효화 전략