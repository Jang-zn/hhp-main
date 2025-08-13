package kr.hhplus.be.server.unit.adapter.storage.jpa.coupon;

import kr.hhplus.be.server.adapter.storage.jpa.CouponHistoryJpaRepository;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("쿠폰 히스토리 JPA 저장소 비즈니스 시나리오")
class CouponHistoryJpaRepositoryTest {

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
    private TestEntityManager testEntityManager;
    
    private CouponHistoryJpaRepository couponHistoryJpaRepository;

    @BeforeEach
    void setUp() {
        couponHistoryJpaRepository = new CouponHistoryJpaRepository(testEntityManager.getEntityManager());
    }

    @Test
    @DisplayName("새로운 쿠폰 이력을 데이터베이스에 저장할 수 있다")
    void canPersistNewCouponHistory() {
        // Given - issuedAt 필수 필드 포함
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .issuedAt(LocalDateTime.now())
                .build();

        // When
        CouponHistory saved = couponHistoryJpaRepository.save(history);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        CouponHistory foundHistory = testEntityManager.find(CouponHistory.class, saved.getId());
        assertThat(foundHistory).isNotNull();
        assertThat(foundHistory.getUserId()).isEqualTo(history.getUserId());
        assertThat(foundHistory.getCouponId()).isEqualTo(history.getCouponId());
    }

    @Test
    @DisplayName("쿠폰 이력을 ID로 조회할 수 있다")
    void canFindCouponHistoryById() {
        // Given
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .issuedAt(LocalDateTime.now())
                .build();
        CouponHistory savedHistory = testEntityManager.persistAndFlush(history);
        testEntityManager.clear();

        // When
        Optional<CouponHistory> found = couponHistoryJpaRepository.findById(savedHistory.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(history.getUserId());
    }

    @Test
    @DisplayName("고객이 특정 쿠폰을 보유하고 있는지 확인할 수 있다")
    void canCheckIfCustomerOwnsCoupon() {
        // Given
        Long userId = 1L, couponId = 1L;
        CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                .userId(userId)
                .couponId(couponId)
                .issuedAt(LocalDateTime.now())
                .build();
        testEntityManager.persistAndFlush(history);
        testEntityManager.clear();

        // When
        boolean exists = couponHistoryJpaRepository.existsByUserIdAndCouponId(userId, couponId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenHistoryNotFoundById() {
        // Given
        Long id = 999L;

        // When
        Optional<CouponHistory> found = couponHistoryJpaRepository.findById(id);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("고객의 쿠폰 이력을 페이지네이션으로 조회할 수 있다")
    void canFindCustomerCouponHistoryWithPagination() {
        // Given
        Long userId = 1L;
        int limit = 10, offset = 0;
        
        for (int i = 0; i < 3; i++) {
            CouponHistory history = TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                    .userId(userId)
                    .couponId((long) i + 1)
                    .issuedAt(LocalDateTime.now())
                    .build();
            testEntityManager.persistAndFlush(history);
        }
        testEntityManager.clear();

        // When
        List<CouponHistory> histories = couponHistoryJpaRepository.findByUserIdWithPagination(userId, limit, offset);

        // Then
        assertThat(histories).hasSize(3);
        assertThat(histories).allMatch(h -> h.getUserId().equals(userId));
    }

    @Test
    @DisplayName("null 쿠폰 이력 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullHistory() {
        // When & Then
        assertThatThrownBy(() -> couponHistoryJpaRepository.save(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("제약 조건 위반 시 예외가 발생한다")
    void throwsExceptionWhenConstraintViolated() {
        // Given - issuedAt 필수 필드 누락
        CouponHistory invalidHistory = CouponHistory.builder()
                .userId(1L)
                .couponId(1L)
                .status(CouponHistoryStatus.ISSUED)
                // issuedAt이 누락됨 - 필수 필드
                .build();

        // When & Then
        assertThatThrownBy(() -> {
            couponHistoryJpaRepository.save(invalidHistory);
            testEntityManager.flush(); // 제약 조건 검증을 위해 flush
        }).isInstanceOf(Exception.class);
    }
}