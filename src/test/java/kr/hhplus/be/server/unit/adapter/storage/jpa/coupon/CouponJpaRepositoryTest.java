package kr.hhplus.be.server.unit.adapter.storage.jpa.coupon;

import kr.hhplus.be.server.adapter.storage.jpa.CouponJpaRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@DisplayName("CouponJpaRepository 비즈니스 시나리오")
class CouponJpaRepositoryTest {

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
    
    private CouponJpaRepository couponJpaRepository;

    @BeforeEach
    void setUp() {
        couponJpaRepository = new CouponJpaRepository(testEntityManager.getEntityManager());
    }

    @Test
    @DisplayName("새로운 쿠폰을 저장할 수 있다")
    void canSaveNewCoupon() {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .code("TEST_COUPON")
                .status(CouponStatus.ACTIVE)
                .build();

        // When
        Coupon savedCoupon = couponJpaRepository.save(coupon);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Coupon foundCoupon = testEntityManager.find(Coupon.class, savedCoupon.getId());
        assertThat(foundCoupon).isNotNull();
        assertThat(foundCoupon.getCode()).isEqualTo("TEST_COUPON");
        assertThat(foundCoupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("기존 쿠폰을 업데이트할 수 있다")
    void canUpdateExistingCoupon() {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .code("ORIGINAL_COUPON")
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon savedCoupon = testEntityManager.persistAndFlush(coupon);
        testEntityManager.clear();
        
        // When - 업데이트를 위해 새 인스턴스 생성 (version 포함)
        Coupon updatedCoupon = Coupon.builder()
                .id(savedCoupon.getId())
                .code("ORIGINAL_COUPON")
                .status(CouponStatus.INACTIVE)
                .discountRate(savedCoupon.getDiscountRate())
                .maxIssuance(savedCoupon.getMaxIssuance())
                .issuedCount(savedCoupon.getIssuedCount())
                .startDate(savedCoupon.getStartDate())
                .endDate(savedCoupon.getEndDate())
                .productId(savedCoupon.getProductId())
                .version(savedCoupon.getVersion()) // version 필드 추가
                .build();
        updatedCoupon = couponJpaRepository.save(updatedCoupon);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Coupon foundCoupon = testEntityManager.find(Coupon.class, updatedCoupon.getId());
        assertThat(foundCoupon.getStatus()).isEqualTo(CouponStatus.INACTIVE);
    }

    @Test
    @DisplayName("ID로 쿠폰을 조회할 수 있다")
    void canFindCouponById() {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .code("FIND_TEST_COUPON")
                .status(CouponStatus.ACTIVE)
                .build();
        Coupon savedCoupon = testEntityManager.persistAndFlush(coupon);
        testEntityManager.clear();

        // When
        Optional<Coupon> foundCoupon = couponJpaRepository.findById(savedCoupon.getId());

        // Then
        assertThat(foundCoupon).isPresent();
        assertThat(foundCoupon.get().getCode()).isEqualTo("FIND_TEST_COUPON");
        assertThat(foundCoupon.get().getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenCouponNotFoundById() {
        // Given
        Long nonExistentId = 999L;

        // When
        Optional<Coupon> foundCoupon = couponJpaRepository.findById(nonExistentId);

        // Then
        assertThat(foundCoupon).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(CouponStatus.class)
    @DisplayName("상태별 쿠폰을 조회할 수 있다")
    void canFindCouponsByStatus(CouponStatus status) {
        // Given
        Coupon coupon1 = TestBuilder.CouponBuilder.defaultCoupon()
                .code("STATUS_TEST_1")
                .status(status)
                .build();
        Coupon coupon2 = TestBuilder.CouponBuilder.defaultCoupon()
                .code("STATUS_TEST_2")
                .status(status)
                .build();
        testEntityManager.persistAndFlush(coupon1);
        testEntityManager.persistAndFlush(coupon2);
        testEntityManager.clear();

        // When
        List<Coupon> coupons = couponJpaRepository.findByStatus(status);

        // Then
        assertThat(coupons).hasSize(2);
        assertThat(coupons).allMatch(c -> c.getStatus() == status);
    }

    @Test
    @DisplayName("만료된 쿠폰을 조회할 수 있다")
    void canFindExpiredCoupons() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon expiredCoupon1 = TestBuilder.CouponBuilder.defaultCoupon()
                .code("EXPIRED_1")
                .status(CouponStatus.ACTIVE)
                .endDate(now.minusDays(1))
                .build();
        Coupon expiredCoupon2 = TestBuilder.CouponBuilder.defaultCoupon()
                .code("EXPIRED_2")
                .status(CouponStatus.ACTIVE)
                .endDate(now.minusDays(2))
                .build();
        testEntityManager.persistAndFlush(expiredCoupon1);
        testEntityManager.persistAndFlush(expiredCoupon2);
        testEntityManager.clear();

        // When
        List<Coupon> expiredCoupons = couponJpaRepository.findExpiredCouponsNotInStatus(now);

        // Then
        assertThat(expiredCoupons).hasSize(2);
        assertThat(expiredCoupons).allMatch(c -> c.getEndDate().isBefore(now));
    }

    @Test
    @DisplayName("특정 상태를 제외한 만료된 쿠폰을 조회할 수 있다")
    void canFindExpiredCouponsExcludingStatuses() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        CouponStatus[] excludeStatuses = {CouponStatus.EXPIRED, CouponStatus.INACTIVE};
        
        Coupon activeCoupon = TestBuilder.CouponBuilder.defaultCoupon()
                .code("ACTIVE_EXPIRED")
                .status(CouponStatus.ACTIVE)
                .endDate(now.minusDays(1))
                .build();
        Coupon expiredCoupon = TestBuilder.CouponBuilder.defaultCoupon()
                .code("ALREADY_EXPIRED")
                .status(CouponStatus.EXPIRED)
                .endDate(now.minusDays(1))
                .build();
        testEntityManager.persistAndFlush(activeCoupon);
        testEntityManager.persistAndFlush(expiredCoupon);
        testEntityManager.clear();

        // When
        List<Coupon> expiredCoupons = couponJpaRepository.findExpiredCouponsNotInStatus(now, excludeStatuses);

        // Then
        assertThat(expiredCoupons).hasSize(1);
        assertThat(expiredCoupons.get(0).getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(CouponStatus.class)
    @DisplayName("상태별 쿠폰 개수를 조회할 수 있다")
    void canCountCouponsByStatus(CouponStatus status) {
        // Given
        for (int i = 0; i < 3; i++) {
            Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                    .code("COUNT_TEST_" + i)
                    .status(status)
                    .build();
            testEntityManager.persistAndFlush(coupon);
        }
        testEntityManager.clear();

        // When
        long count = couponJpaRepository.countByStatus(status);

        // Then
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("해당 상태의 쿠폰이 없는 경우 0을 반환한다")
    void returnsZeroWhenNoCouponsWithStatus() {
        // Given - 다른 상태의 쿠폰만 저장
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .code("OTHER_STATUS")
                .status(CouponStatus.ACTIVE)
                .build();
        testEntityManager.persistAndFlush(coupon);
        testEntityManager.clear();

        // When
        long count = couponJpaRepository.countByStatus(CouponStatus.EXPIRED);

        // Then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("null 쿠폰 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullCoupon() {
        // When & Then
        assertThatThrownBy(() -> couponJpaRepository.save(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("조회 중 예외 발생 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindExceptionOccurs() {
        // Given - 존재하지 않는 ID로 조회
        Long nonExistentId = 999999L;

        // When
        Optional<Coupon> result = couponJpaRepository.findById(nonExistentId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("제약 조건 위반 시 예외가 발생한다")
    void throwsExceptionWhenConstraintViolated() {
        // Given - 필수 필드가 null인 쿠폰
        Coupon invalidCoupon = Coupon.builder()
                .code(null) // code는 필수 필드
                .status(CouponStatus.ACTIVE)
                .build();

        // When & Then
        assertThatThrownBy(() -> {
            couponJpaRepository.save(invalidCoupon);
            testEntityManager.flush(); // 제약 조건 검증을 위해 flush
        }).isInstanceOf(Exception.class);
    }
}