package kr.hhplus.be.server.unit.adapter.storage.jpa.coupon;

import kr.hhplus.be.server.adapter.storage.jpa.CouponJpaRepository;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("CouponJpaRepository 단위 테스트")
class CouponJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Coupon> couponQuery;

    @Mock
    private TypedQuery<Long> countQuery;

    private CouponJpaRepository couponJpaRepository;

    @BeforeEach
    void setUp() {
        couponJpaRepository = new CouponJpaRepository(entityManager);
    }

    @Nested
    @DisplayName("쿠폰 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 쿠폰 저장")
        void save_NewCoupon_Success() {
            // given
            Coupon coupon = Coupon.builder()
                    .code("TEST_COUPON")
                    .status(CouponStatus.ACTIVE)
                    .build();

            doNothing().when(entityManager).persist(coupon);

            // when
            Coupon savedCoupon = couponJpaRepository.save(coupon);

            // then
            assertThat(savedCoupon).isEqualTo(coupon);
            verify(entityManager, times(1)).persist(coupon);
        }

        @Test
        @DisplayName("성공케이스: 기존 쿠폰 업데이트")
        void save_ExistingCoupon_Success() {
            // given
            Coupon coupon = Coupon.builder()
                    .id(1L)
                    .code("UPDATED_COUPON")
                    .status(CouponStatus.INACTIVE)
                    .build();

            when(entityManager.merge(coupon)).thenReturn(coupon);

            // when
            Coupon savedCoupon = couponJpaRepository.save(coupon);

            // then
            assertThat(savedCoupon).isEqualTo(coupon);
            verify(entityManager, times(1)).merge(coupon);
        }
    }

    @Nested
    @DisplayName("ID로 조회 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("성공케이스: ID로 쿠폰 조회")
        void findById_Success() {
            // given
            Long id = 1L;
            Coupon expectedCoupon = Coupon.builder()
                    .id(id)
                    .code("TEST_COUPON")
                    .status(CouponStatus.ACTIVE)
                    .build();

            when(entityManager.find(Coupon.class, id)).thenReturn(expectedCoupon);

            // when
            Optional<Coupon> foundCoupon = couponJpaRepository.findById(id);

            // then
            assertThat(foundCoupon).isPresent();
            assertThat(foundCoupon.get()).isEqualTo(expectedCoupon);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 ID로 조회")
        void findById_NotFound() {
            // given
            Long id = 999L;
            when(entityManager.find(Coupon.class, id)).thenReturn(null);

            // when
            Optional<Coupon> foundCoupon = couponJpaRepository.findById(id);

            // then
            assertThat(foundCoupon).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태별 조회 테스트")
    class FindByStatusTests {

        @ParameterizedTest
        @EnumSource(CouponStatus.class)
        @DisplayName("성공케이스: 상태별 쿠폰 조회")
        void findByStatus_Success(CouponStatus status) {
            // given
            List<Coupon> expectedCoupons = Arrays.asList(
                    Coupon.builder().id(1L).status(status).build(),
                    Coupon.builder().id(2L).status(status).build()
            );

            when(entityManager.createQuery(anyString(), eq(Coupon.class))).thenReturn(couponQuery);
            when(couponQuery.setParameter("status", status)).thenReturn(couponQuery);
            when(couponQuery.getResultList()).thenReturn(expectedCoupons);

            // when
            List<Coupon> coupons = couponJpaRepository.findByStatus(status);

            // then
            assertThat(coupons).hasSize(2);
            assertThat(coupons).allMatch(c -> c.getStatus() == status);
        }
    }

    @Nested
    @DisplayName("만료된 쿠폰 조회 테스트")
    class FindExpiredCouponsTests {

        @Test
        @DisplayName("성공케이스: 만료된 쿠폰 조회 (제외 상태 없음)")
        void findExpiredCouponsNotInStatus_NoExcludeStatuses() {
            // given
            LocalDateTime now = LocalDateTime.now();
            List<Coupon> expectedCoupons = Arrays.asList(
                    Coupon.builder().id(1L).endDate(now.minusDays(1)).build(),
                    Coupon.builder().id(2L).endDate(now.minusDays(2)).build()
            );

            when(entityManager.createQuery(anyString(), eq(Coupon.class))).thenReturn(couponQuery);
            when(couponQuery.setParameter("now", now)).thenReturn(couponQuery);
            when(couponQuery.getResultList()).thenReturn(expectedCoupons);

            // when
            List<Coupon> coupons = couponJpaRepository.findExpiredCouponsNotInStatus(now);

            // then
            assertThat(coupons).hasSize(2);
        }

        @Test
        @DisplayName("성공케이스: 만료된 쿠폰 조회 (제외 상태 있음)")
        void findExpiredCouponsNotInStatus_WithExcludeStatuses() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponStatus[] excludeStatuses = {CouponStatus.EXPIRED, CouponStatus.INACTIVE};
            List<Coupon> expectedCoupons = Arrays.asList(
                    Coupon.builder().id(1L).status(CouponStatus.ACTIVE).endDate(now.minusDays(1)).build()
            );

            when(entityManager.createQuery(anyString(), eq(Coupon.class))).thenReturn(couponQuery);
            when(couponQuery.setParameter("now", now)).thenReturn(couponQuery);
            when(couponQuery.setParameter("excludeStatuses", Arrays.asList(excludeStatuses))).thenReturn(couponQuery);
            when(couponQuery.getResultList()).thenReturn(expectedCoupons);

            // when
            List<Coupon> coupons = couponJpaRepository.findExpiredCouponsNotInStatus(now, excludeStatuses);

            // then
            assertThat(coupons).hasSize(1);
            assertThat(coupons.get(0).getStatus()).isEqualTo(CouponStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("상태별 개수 조회 테스트")
    class CountByStatusTests {

        @ParameterizedTest
        @EnumSource(CouponStatus.class)
        @DisplayName("성공케이스: 상태별 쿠폰 개수 조회")
        void countByStatus_Success(CouponStatus status) {
            // given
            long expectedCount = 5L;

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("status", status)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(expectedCount);

            // when
            long count = couponJpaRepository.countByStatus(status);

            // then
            assertThat(count).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("성공케이스: 해당 상태의 쿠폰이 없는 경우")
        void countByStatus_NoResults() {
            // given
            CouponStatus status = CouponStatus.ACTIVE;

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("status", status)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            // when
            long count = couponJpaRepository.countByStatus(status);

            // then
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: persist 중 예외 발생")
        void save_PersistException() {
            // given
            Coupon coupon = Coupon.builder().code("TEST_COUPON").build();
            doThrow(new RuntimeException("DB 오류")).when(entityManager).persist(coupon);

            // when & then
            assertThatThrownBy(() -> couponJpaRepository.save(coupon))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 오류");
        }

        @Test
        @DisplayName("실패케이스: 조회 중 예외 발생")
        void findById_Exception() {
            // given
            Long id = 1L;
            when(entityManager.find(Coupon.class, id))
                    .thenThrow(new RuntimeException("데이터베이스 오류"));

            // when
            Optional<Coupon> result = couponJpaRepository.findById(id);

            // then
            assertThat(result).isEmpty();
        }
    }
}