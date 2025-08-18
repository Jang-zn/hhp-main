# Controller 테스트 최적화 분석: @SpringBootTest vs @WebMvcTest

## 📋 개요

현재 프로젝트의 Controller 테스트는 `@SpringBootTest`를 사용하여 전체 Spring 컨텍스트를 로딩하고 있습니다. 
성능 최적화를 위해 `@WebMvcTest`로 전환을 시도했으나 복잡성과 모킹 이슈로 인해 실패했습니다.

본 문서는 실패 원인을 분석하고 향후 개선 방안을 제시합니다.

## 🔍 현재 상황 분석

### 현재 테스트 구조
```java
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("쿠폰 목록 조회 컨트롤러 API")
class GetCouponListControllerTest {
    
    @MockitoBean
    private CouponService couponService;
    
    // 테스트 메서드들...
}
```

### 성능 지표
- **테스트 실행 시간**: 0.164초 ~ 0.254초
- **컨텍스트 로딩 시간**: 약 5초 (최초 한 번)
- **성공률**: 100%

## ❌ @WebMvcTest 전환 실패 원인

### 1. 복잡한 의존성 체인

**문제점:**
```java
@WebMvcTest(CouponController.class)
class GetCouponListControllerTest {
    // IllegalStateException: Failed to load ApplicationContext
}
```

**원인 분석:**
- CouponController → CouponService → Multiple UseCases → Repository Ports
- @WebMvcTest는 웹 레이어만 로드하려 하지만 ServerApplication 전체를 참조
- Spring Data JPA Repository Port 인터페이스들이 Bean으로 등록되지 않음

### 2. 서로 다른 컨텍스트 설정

**@SpringBootTest 컨텍스트:**
```java
- 전체 애플리케이션 컨텍스트
- @EnableJpaRepositories 설정 적용
- Testcontainers MySQL 연결
- Redis 연결
- 모든 자동 설정 활성화
```

**@WebMvcTest 컨텍스트:**
```java
- 웹 레이어만 로드
- JPA Repository 설정 누락
- 데이터베이스 연결 불필요
- 제한된 자동 설정
```

### 3. 모킹 복잡성

**현재 모킹 구조:**
```java
@MockitoBean
private CouponService couponService;

// 테스트에서 필요한 모킹
when(couponService.getCouponList(userId, limit, offset)).thenReturn(couponHistories);
when(couponService.getCouponById(1L)).thenReturn(coupon1);
when(couponService.getCouponById(2L)).thenReturn(coupon2);
```

**@WebMvcTest에서 추가로 필요한 모킹:**
- 모든 UseCase Bean들
- Repository Port Bean들
- 트랜잭션 관련 Bean들
- 캐시 관련 Bean들

## 🚧 극복해야 할 기술적 장벽

### 1. 아키텍처 복잡성
```
Controller → Service → UseCase → Repository Port → JPA Repository
```
- 각 레이어별 Bean 등록 필요
- 의존성 체인이 깊음
- Clean Architecture 패턴으로 인한 다중 레이어

### 2. Spring Data JPA 설정
```java
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.domain.port.storage")
```
- Repository Port 인터페이스들의 Bean 등록
- @WebMvcTest에서는 JPA 설정이 로드되지 않음

### 3. 테스트 컨테이너 설정
```java
@Testcontainers
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
```
- @WebMvcTest에서는 데이터베이스 연결 불필요
- 하지만 현재 테스트가 실제 DB 검증을 포함

## 💡 해결 방안

### Phase 1: 점진적 분리 (단기)

#### 1.1 순수 웹 레이어 테스트 분리
```java
@WebMvcTest(CouponController.class)
@Import({CouponService.class}) // 필요한 서비스만 Import
class CouponControllerWebTest {
    
    @MockBean
    private GetCouponListUseCase getCouponListUseCase;
    
    @MockBean 
    private GetCouponByIdUseCase getCouponByIdUseCase;
    
    // HTTP 요청/응답 검증에만 집중
}
```

#### 1.2 통합 테스트와 단위 테스트 분리
```
src/test/java/
├── unit/
│   ├── controller/web/     # @WebMvcTest (HTTP 레이어만)
│   └── controller/integration/  # @SpringBootTest (현재 방식 유지)
├── integration/
└── e2e/
```

### Phase 2: 테스트 슬라이스 최적화 (중기)

#### 2.1 커스텀 테스트 어노테이션 생성
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({
    CouponService.class,
    // 필요한 핵심 컴포넌트들만
})
@MockBean({
    GetCouponListUseCase.class,
    GetCouponByIdUseCase.class,
    IssueCouponUseCase.class
})
public @interface CouponControllerTest {
}
```

#### 2.2 TestConfiguration 활용
```java
@TestConfiguration
public class ControllerTestConfig {
    
    @Bean
    @Primary
    public CouponService mockCouponService() {
        return Mockito.mock(CouponService.class);
    }
    
    // 필요한 모든 Mock Bean 정의
}
```

### Phase 3: 완전한 테스트 아키텍처 개선 (장기)

#### 3.1 테스트 더블 팩토리 패턴
```java
public class CouponTestDoubleFactory {
    
    public static CouponService createMockService() {
        CouponService mock = Mockito.mock(CouponService.class);
        // 기본 모킹 설정
        return mock;
    }
    
    public static List<CouponHistory> createSampleCouponHistories() {
        // 테스트 데이터 생성
    }
}
```

#### 3.2 레이어별 테스트 전략
```java
// 1. HTTP 레이어 테스트 (Fast)
@WebMvcTest
class CouponControllerHttpTest { } // 0.01초

// 2. 서비스 레이어 테스트 (Medium)  
@ExtendWith(MockitoExtension.class)
class CouponServiceTest { } // 0.05초

// 3. 통합 테스트 (Slow)
@SpringBootTest
class CouponControllerIntegrationTest { } // 0.2초
```

## 📊 예상 성능 개선 효과

### 현재 vs 개선 후

| 테스트 유형 | 현재 시간 | 개선 후 시간 | 개선율 |
|------------|----------|------------|--------|
| HTTP 레이어 테스트 | 0.2초 | 0.01초 | 95% |
| 서비스 레이어 테스트 | 0.2초 | 0.05초 | 75% |
| 통합 테스트 | 0.2초 | 0.2초 | 0% |

### 전체 테스트 수트 기준
- **현재**: 20개 Controller 테스트 × 0.2초 = 4초
- **개선 후**: 
  - HTTP 테스트 20개 × 0.01초 = 0.2초
  - 통합 테스트 5개 × 0.2초 = 1초
  - **총 1.2초 (70% 개선)**

## 🛠 구현 로드맵

### Step 1: 프로토타입 개발 (1주)
- [ ] 단일 Controller에 대해 @WebMvcTest 성공 사례 구현
- [ ] 커스텀 테스트 어노테이션 프로토타입
- [ ] 성능 벤치마크 수행

### Step 2: 패턴 정립 (1주)  
- [ ] 성공한 패턴을 다른 Controller에 적용
- [ ] TestConfiguration 표준화
- [ ] 테스트 더블 팩토리 구현

### Step 3: 전체 적용 (2주)
- [ ] 모든 Controller 테스트 마이그레이션
- [ ] CI/CD 파이프라인 최적화
- [ ] 문서화 및 팀 교육

## 🚨 리스크 및 대응 방안

### 리스크 1: 테스트 신뢰성 저하
**대응방안**: 
- 통합 테스트를 일부 유지
- Critical Path에 대해서는 E2E 테스트 추가

### 리스크 2: 개발 생산성 저하
**대응방안**:
- 점진적 마이그레이션
- 기존 테스트 병행 유지

### 리스크 3: 모킹 복잡성 증가
**대응방안**:
- 테스트 더블 팩토리 패턴 활용
- 재사용 가능한 모킹 유틸리티 개발

## 🎯 결론 및 권장사항

### 즉시 실행 가능한 개선사항
1. **테스트 분류 체계 도입**: 현재 테스트를 Fast/Medium/Slow로 분류
2. **선택적 테스트 실행**: 개발 시에는 Fast 테스트만, CI에서는 전체 실행
3. **병렬 테스트 실행**: Gradle에서 parallel 옵션 활용

### 중장기 개선 목표
1. **@WebMvcTest 적용률 80% 달성**: HTTP 레이어 테스트는 모두 @WebMvcTest로
2. **평균 테스트 실행 시간 0.05초 이하**: 현재 대비 75% 성능 개선
3. **테스트 피라미드 구조 완성**: Unit(70%) > Integration(20%) > E2E(10%)

현재 상황에서는 **성능보다 안정성이 우선**이므로, 당분간 @SpringBootTest를 유지하되 위 로드맵에 따라 점진적으로 개선해 나가는 것을 권장합니다.

## 🔄 즉시 적용된 개선사항 (2025.08.18)

### 공통 Base 클래스를 통한 코드 중복 제거

**Before:**
```java
// 각 Controller 테스트마다 동일한 설정 반복 (~30줄)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GetCouponListControllerTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private CouponService couponService;
    
    // 비즈니스 테스트 로직...
}
```

**After:**
```java
// ControllerTestBase.java - 공통 설정을 한 곳에 중앙화
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
public abstract class ControllerTestBase {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
}

// 각 테스트는 간단하게! (~3줄)
@DisplayName("쿠폰 목록 조회 컨트롤러 API")
class GetCouponListControllerTest extends ControllerTestBase {
    
    @MockitoBean
    private CouponService couponService;
    
    // 비즈니스 로직 테스트에만 집중!
}
```

### 개선 효과

1. **코드 중복 90% 제거**: 각 Controller 테스트에서 **30줄 → 3줄**로 설정 코드 축소
2. **유지보수성 향상**: 공통 설정 변경 시 한 곳(ControllerTestBase)만 수정
3. **가독성 개선**: 테스트 비즈니스 로직에 더 집중 가능
4. **일관성 보장**: 모든 Controller 테스트가 동일한 환경에서 실행
5. **신규 Controller 테스트 개발 속도 향상**: 상속만 받으면 즉시 테스트 환경 구축

### 추가 개선 가능 사항

ControllerTestBase에 더 많은 공통 유틸리티를 추가할 수 있습니다:

```java
public abstract class ControllerTestBase {
    // ... 기존 설정 ...
    
    /**
     * 성공 응답 검증 헬퍼 메서드
     */
    protected ResultActions expectSuccessResponse(ResultActions result) throws Exception {
        return result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S001"));
    }
    
    /**
     * 에러 응답 검증 헬퍼 메서드 
     */
    protected ResultActions expectErrorResponse(ResultActions result, String errorCode) throws Exception {
        return result
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(errorCode));
    }
    
    /**
     * 공통 테스트 데이터 생성 헬퍼
     */
    protected CouponHistory createSampleCouponHistory(Long userId, Long couponId) {
        return TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .userId(userId)
                .couponId(couponId)
                .build();
    }
}
```

이 개선사항만으로도 **개발자 경험이 크게 향상**되었으며, 향후 @WebMvcTest 전환 시에도 이 Base 클래스를 활용할 수 있습니다.