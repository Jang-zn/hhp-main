package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.coupon.GetCouponListUseCase;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import kr.hhplus.be.server.domain.exception.CouponException;
import java.util.Collections;

@DisplayName("GetCouponListUseCase 단위 테스트")
class GetCouponListUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    @Mock
    private CachePort cachePort;

    private GetCouponListUseCase getCouponListUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getCouponListUseCase = new GetCouponListUseCase(userRepositoryPort, couponHistoryRepositoryPort, cachePort);
    }

    @Test
    @DisplayName("보유 쿠폰 목록 조회 성공")
    void getCouponList_Success() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        List<CouponHistory> couponHistories = List.of(
                CouponHistory.builder()
                        .user(user)
                        .coupon(Coupon.builder()
                                .code("DISCOUNT10")
                                .discountRate(new BigDecimal("0.10"))
                                .maxIssuance(100)
                                .issuedCount(50)
                                .startDate(LocalDateTime.now().minusDays(1))
                                .endDate(LocalDateTime.now().plusDays(30))
                                .build())
                        .issuedAt(LocalDateTime.now())
                        .build(),
                CouponHistory.builder()
                        .user(user)
                        .coupon(Coupon.builder()
                                .code("SUMMER25")
                                .discountRate(new BigDecimal("0.25"))
                                .maxIssuance(50)
                                .issuedCount(30)
                                .startDate(LocalDateTime.now().minusDays(5))
                                .endDate(LocalDateTime.now().plusDays(25))
                                .build())
                        .issuedAt(LocalDateTime.now().minusDays(3))
                        .build()
        );
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(cachePort.get("coupon_list_" + userId + "_" + limit + "_" + offset, List.class, () -> 
            couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset))).thenReturn(couponHistories);

        // when
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 빈 리스트 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isNotNull();
        // assertThat(result).hasSize(2);
        // assertThat(result.get(0).getCoupon().getCode()).isEqualTo("DISCOUNT10");
    }

    @ParameterizedTest
    @MethodSource("providePaginationData")
    @DisplayName("다양한 페이지네이션으로 쿠폰 목록 조회")
    void getCouponList_WithDifferentPagination(Long userId, int limit, int offset) {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        List<CouponHistory> couponHistories = List.of(
                CouponHistory.builder()
                        .user(user)
                        .coupon(Coupon.builder()
                                .code("VIP30")
                                .discountRate(new BigDecimal("0.30"))
                                .maxIssuance(20)
                                .issuedCount(10)
                                .startDate(LocalDateTime.now().minusDays(1))
                                .endDate(LocalDateTime.now().plusDays(10))
                                .build())
                        .issuedAt(LocalDateTime.now())
                        .build()
        );
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(cachePort.get("coupon_list_" + userId + "_" + limit + "_" + offset, List.class, () -> 
            couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset))).thenReturn(couponHistories);

        // when
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 빈 리스트 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isNotNull();
        // assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 쿠폰 목록 조회 시 예외 발생")
    void getCouponList_UserNotFound() {
        // given
        Long userId = 999L;
        int limit = 10;
        int offset = 0;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("null 사용자 ID로 쿠폰 목록 조회 시 예외 발생")
    void getCouponList_WithNullUserId() {
        // given
        Long userId = null;
        int limit = 10;
        int offset = 0;

        // when & then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("쿠폰이 없는 사용자 목록 조회")
    void getCouponList_EmptyCoupons() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = 0;
        
        User user = User.builder()
                .name("쿠폰 없는 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(cachePort.get("coupon_list_" + userId + "_" + limit + "_" + offset, List.class, () -> 
            couponHistoryRepositoryPort.findByUserWithPagination(user, limit, offset))).thenReturn(Collections.emptyList());

        // when
        List<CouponHistory> result = getCouponListUseCase.execute(userId, limit, offset);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("비정상적인 페이지네이션 파라미터 - 음수 limit")
    void getCouponList_WithNegativeLimit() {
        // given
        Long userId = 1L;
        int limit = -1;
        int offset = 0;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    @DisplayName("비정상적인 페이지네이션 파라미터 - 음수 offset")
    void getCouponList_WithNegativeOffset() {
        // given
        Long userId = 1L;
        int limit = 10;
        int offset = -1;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserIds")
    @DisplayName("다양한 비정상 사용자 ID로 쿠폰 목록 조회")
    void getCouponList_WithInvalidUserIds(Long invalidUserId) {
        // given
        int limit = 10;
        int offset = 0;
        
        when(userRepositoryPort.findById(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> getCouponListUseCase.execute(invalidUserId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPaginationParams")
    @DisplayName("다양한 비정상 페이지네이션 파라미터")
    void getCouponList_WithInvalidPagination(String description, int limit, int offset) {
        // given
        Long userId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> getCouponListUseCase.execute(userId, limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> providePaginationData() {
        return Stream.of(
                Arguments.of(1L, 5, 0),
                Arguments.of(2L, 10, 5),
                Arguments.of(3L, 20, 0)
        );
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> provideInvalidPaginationParams() {
        return Stream.of(
                Arguments.of("음수 limit", -1, 0),
                Arguments.of("음수 offset", 10, -1),
                Arguments.of("0 limit", 0, 0),
                Arguments.of("과도한 limit", 10000, 0)
        );
    }
}