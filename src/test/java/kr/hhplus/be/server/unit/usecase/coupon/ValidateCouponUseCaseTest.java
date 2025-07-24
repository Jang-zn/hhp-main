package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.port.storage.CouponRepositoryPort;
import kr.hhplus.be.server.domain.usecase.coupon.ValidateCouponUseCase;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("ValidateCouponUseCase 단위 테스트")
class ValidateCouponUseCaseTest {

    @Mock
    private CouponRepositoryPort couponRepositoryPort;

    private ValidateCouponUseCase validateCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validateCouponUseCase = new ValidateCouponUseCase(couponRepositoryPort);
    }

    @Test
    @DisplayName("유효한 쿠폰들 검증 성공")
    void validateCoupon_Success() {
        // given
        List<Long> couponIds = List.of(1L, 2L, 3L);
        
        Coupon coupon1 = createCoupon(1L, "COUPON1");
        Coupon coupon2 = createCoupon(2L, "COUPON2");
        Coupon coupon3 = createCoupon(3L, "COUPON3");
        
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponRepositoryPort.findById(2L)).thenReturn(Optional.of(coupon2));
        when(couponRepositoryPort.findById(3L)).thenReturn(Optional.of(coupon3));

        // when & then
        assertThatCode(() -> validateCouponUseCase.execute(couponIds))
                .doesNotThrowAnyException();
        
        verify(couponRepositoryPort, times(3)).findById(any());
    }

    @Test
    @DisplayName("빈 쿠폰 목록으로 검증 시 성공")
    void validateCoupon_EmptyList() {
        // given
        List<Long> couponIds = List.of();

        // when & then
        assertThatCode(() -> validateCouponUseCase.execute(couponIds))
                .doesNotThrowAnyException();
        
        verify(couponRepositoryPort, never()).findById(any());
    }

    @Test
    @DisplayName("null 쿠폰 목록으로 검증 시 성공")
    void validateCoupon_NullList() {
        // given
        List<Long> couponIds = null;

        // when & then
        assertThatCode(() -> validateCouponUseCase.execute(couponIds))
                .doesNotThrowAnyException();
        
        verify(couponRepositoryPort, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 검증 시 예외 발생")
    void validateCoupon_CouponNotFound() {
        // given
        List<Long> couponIds = List.of(999L);
        
        when(couponRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> validateCouponUseCase.execute(couponIds))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(CouponException.Messages.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("일부 쿠폰이 존재하지 않는 경우 예외 발생")
    void validateCoupon_PartialNotFound() {
        // given
        List<Long> couponIds = List.of(1L, 999L, 2L);
        
        Coupon coupon1 = createCoupon(1L, "COUPON1");
        
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> validateCouponUseCase.execute(couponIds))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(CouponException.Messages.COUPON_NOT_FOUND);
        
        verify(couponRepositoryPort).findById(1L);
        verify(couponRepositoryPort).findById(999L);
        verify(couponRepositoryPort, never()).findById(2L); // 예외로 인해 호출되지 않음
    }

    @ParameterizedTest
    @MethodSource("provideInvalidCouponIds")
    @DisplayName("null 쿠폰 ID가 포함된 경우 예외 발생")
    void validateCoupon_WithNullCouponId(String description, List<Long> couponIds) {
        // given - mock valid coupon IDs if they exist
        for (Long id : couponIds) {
            if (id != null) {
                Coupon coupon = createCoupon(id, "COUPON" + id);
                when(couponRepositoryPort.findById(id)).thenReturn(Optional.of(coupon));
            }
        }
        
        // when & then
        assertThatThrownBy(() -> validateCouponUseCase.execute(couponIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Coupon ID cannot be null");
    }

    @Test
    @DisplayName("단일 쿠폰 검증 성공")
    void validateCoupon_SingleCoupon() {
        // given
        List<Long> couponIds = List.of(1L);
        Coupon coupon = createCoupon(1L, "SINGLE_COUPON");
        
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(coupon));

        // when & then
        assertThatCode(() -> validateCouponUseCase.execute(couponIds))
                .doesNotThrowAnyException();
        
        verify(couponRepositoryPort).findById(1L);
    }

    @Test
    @DisplayName("많은 수의 쿠폰 검증")
    void validateCoupon_ManyCoupons() {
        // given
        List<Long> couponIds = List.of(1L, 2L, 3L, 4L, 5L);
        
        for (Long id : couponIds) {
            Coupon coupon = createCoupon(id, "COUPON" + id);
            when(couponRepositoryPort.findById(id)).thenReturn(Optional.of(coupon));
        }

        // when & then
        assertThatCode(() -> validateCouponUseCase.execute(couponIds))
                .doesNotThrowAnyException();
        
        verify(couponRepositoryPort, times(5)).findById(any());
    }

    @Test
    @DisplayName("중복된 쿠폰 ID가 있어도 각각 검증")
    void validateCoupon_DuplicateIds() {
        // given
        List<Long> couponIds = List.of(1L, 1L, 2L, 1L);
        
        Coupon coupon1 = createCoupon(1L, "COUPON1");
        Coupon coupon2 = createCoupon(2L, "COUPON2");
        
        when(couponRepositoryPort.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponRepositoryPort.findById(2L)).thenReturn(Optional.of(coupon2));

        // when & then
        assertThatCode(() -> validateCouponUseCase.execute(couponIds))
                .doesNotThrowAnyException();
        
        verify(couponRepositoryPort, times(3)).findById(1L); // 3번 호출
        verify(couponRepositoryPort, times(1)).findById(2L); // 1번 호출
    }

    private Coupon createCoupon(Long id, String code) {
        return Coupon.builder()
                .id(id)
                .code(code)
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();
    }

    private static Stream<Arguments> provideInvalidCouponIds() {
        return Stream.of(
                Arguments.of("single null", Arrays.asList((Long) null)),
                Arguments.of("mixed with null", Arrays.asList(1L, null, 2L)),
                Arguments.of("all nulls", Arrays.asList(null, null))
        );
    }
}