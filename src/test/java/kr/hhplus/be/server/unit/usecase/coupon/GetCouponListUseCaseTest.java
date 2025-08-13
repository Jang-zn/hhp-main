package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GetCouponListUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 사용자 쿠폰 목록 조회 유스케이스의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 쿠폰 목록 조회 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("사용자 쿠폰 목록 조회 유스케이스 비즈니스 시나리오")
class GetCouponListUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    

    private GetCouponListUseCase getCouponListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getCouponListUseCase = new GetCouponListUseCase(userRepositoryPort, couponHistoryRepositoryPort);
    }

    // === 기본 쿠폰 목록 조회 시나리오 ===

    @Test
    @DisplayName("사용자의 보유 쿠폰 목록을 성공적으로 조회한다")
    void canRetrieveUserCouponListSuccessfully() {
        // Given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        Coupon coupon1 = TestBuilder.CouponBuilder.defaultCoupon()
                .id(1L)
                .code("DISCOUNT10")
                .discountRate(new BigDecimal("0.10"))
                .build();
        
        Coupon coupon2 = TestBuilder.CouponBuilder.defaultCoupon()
                .id(2L)
                .code("SUMMER25")
                .discountRate(new BigDecimal("0.25"))
                .build();

        List<CouponHistory> couponHistories = List.of(
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .userId(userId)
                        .couponId(coupon1.getId())
                        .status(CouponHistoryStatus.ISSUED)
                        .build(),
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .userId(userId)
                        .couponId(coupon2.getId())
                        .status(CouponHistoryStatus.ISSUED)
                        .build()
        );
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(couponHistoryRepositoryPort.findByUserIdWithPagination(userId, limit, offset)).thenReturn(couponHistories);

        // When
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCouponId()).isEqualTo(coupon1.getId());
        assertThat(result.get(1).getCouponId()).isEqualTo(coupon2.getId());
        
        verify(userRepositoryPort).existsById(userId);
        verify(couponHistoryRepositoryPort).findByUserIdWithPagination(userId, limit, offset);
    }

    @ParameterizedTest
    @MethodSource("providePaginationData")
    @DisplayName("다양한 페이지네이션으로 쿠폰 목록을 조회할 수 있다")
    void canRetrieveCouponListWithVariousPagination(Long userId, int limit, int offset) {
        // Given
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(3L)
                .code("VIP30")
                .discountRate(new BigDecimal("0.30"))
                .build();

        List<CouponHistory> couponHistories = List.of(
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .userId(userId)
                        .couponId(coupon.getId())
                        .status(CouponHistoryStatus.ISSUED)
                        .build()
        );
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(couponHistoryRepositoryPort.findByUserIdWithPagination(userId, limit, offset)).thenReturn(couponHistories);

        // When
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getCouponId()).isEqualTo(coupon.getId());
    }

    @Test
    @DisplayName("쿠폰이 없는 사용자는 빈 목록을 반환한다")
    void returnsEmptyListForUserWithNoCoupons() {
        // Given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(couponHistoryRepositoryPort.findByUserIdWithPagination(userId, limit, offset)).thenReturn(Collections.emptyList());

        // When
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(userRepositoryPort).existsById(userId);
        verify(couponHistoryRepositoryPort).findByUserIdWithPagination(userId, limit, offset);
    }

    // === 예외 처리 시나리오 ===

    @Test
    @DisplayName("존재하지 않는 사용자 쿠폰 목록 조회 시 예외가 발생한다")
    void throwsExceptionWhenUserNotFound() {
        // Given
        Long userId = 999L;
        int limit = 10;
        int offset = 0;
        
        when(userRepositoryPort.existsById(userId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(UserException.NotFound.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("null 사용자 ID로 쿠폰 목록 조회 시 예외가 발생한다")
    void throwsExceptionForNullUserId() {
        // Given
        Long userId = null;
        int limit = 10;
        int offset = 0;

        // When & Then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("다양한 비정상 사용자 ID로 쿠폰 목록 조회 시 적절한 예외가 발생한다")
    void throwsExceptionForInvalidUserIds(Long invalidUserId) {
        // Given
        int limit = 10;
        int offset = 0;
        
        if (invalidUserId != null) {
            when(userRepositoryPort.existsById(invalidUserId)).thenReturn(false);
            
            // When & Then
            assertThatThrownBy(() -> getCouponListUseCase.execute(invalidUserId, limit, offset))
                    .isInstanceOf(UserException.NotFound.class)
                    .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
        } else {
            // When & Then
            assertThatThrownBy(() -> getCouponListUseCase.execute(invalidUserId, limit, offset))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }
    }

    // === 페이지네이션 관련 시나리오 ===

    @Test
    @DisplayName("음수 limit으로 쿠폰 목록 조회 시 예외가 발생한다")
    void throwsExceptionForNegativeLimit() {
        // Given
        Long userId = 1L;
        int limit = -1;
        int offset = 0;
        
        // When & Then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be greater than 0");
    }

    @Test
    @DisplayName("음수 offset으로 쿠폰 목록 조회 시 예외가 발생한다")
    void throwsExceptionForNegativeOffset() {
        // Given
        Long userId = 1L;
        int limit = 10;
        int offset = -1;
        
        // When & Then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Offset must be non-negative");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("다양한 비정상 페이지네이션 파라미터에 대해 예외가 발생한다")
    void throwsExceptionForInvalidPaginationParams(String description, int limit, int offset) {
        // Given
        Long userId = 1L;
        
        // When & Then (validation happens before user lookup)
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DB에서 직접 조회한다")
    void retrievesFromDatabase() {
        // Given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        Coupon coupon = TestBuilder.CouponBuilder.defaultCoupon()
                .id(4L)
                .code("FALLBACK")
                .discountRate(new BigDecimal("0.20"))
                .build();

        List<CouponHistory> couponHistories = List.of(
                TestBuilder.CouponHistoryBuilder.defaultCouponHistory()
                        .userId(userId)
                        .couponId(coupon.getId())
                        .status(CouponHistoryStatus.ISSUED)
                        .build()
        );
        
        when(userRepositoryPort.existsById(userId)).thenReturn(true);
        when(couponHistoryRepositoryPort.findByUserIdWithPagination(userId, limit, offset))
                .thenReturn(couponHistories);

        // When
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponId()).isEqualTo(coupon.getId());
        
        verify(couponHistoryRepositoryPort).findByUserIdWithPagination(userId, limit, offset);
    }

    // === 헬퍼 메서드 ===

    static Stream<Arguments> providePaginationData() {
        return Stream.of(
                Arguments.of(1L, 5, 0),
                Arguments.of(2L, 10, 5),
                Arguments.of(3L, 20, 0),
                Arguments.of(4L, 15, 10),
                Arguments.of(5L, 25, 20)
        );
    }

    static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of((Long) null),
                Arguments.of(999L),
                Arguments.of(888L),
                Arguments.of(777L)
        );
    }

    static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                Arguments.of("음수 limit", -1, 0),
                Arguments.of("음수 offset", 10, -1),
                Arguments.of("0 limit", 0, 0),
                Arguments.of("과도한 limit", 1001, 0)
        );
    }
}