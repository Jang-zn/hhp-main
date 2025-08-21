package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.usecase.coupon.UseCouponUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UseCouponUseCase 비즈니스 시나리오 테스트
 * 
 * Why: 쿠폰 사용 유스케이스의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 실제 고객의 쿠폰 사용 시나리오를 반영한 유스케이스 레이어 테스트로 구성
 */
@DisplayName("쿠폰 사용 유스케이스 비즈니스 시나리오")
class UseCouponUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;
    
    @Mock
    private KeyGenerator keyGenerator;

    private UseCouponUseCase useCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCouponUseCase = new UseCouponUseCase(userRepositoryPort, couponHistoryRepositoryPort, lockingPort, cachePort, keyGenerator);
    }

    @Test
    @DisplayName("고객이 보유한 여러 쿠폰을 주문에서 성공적으로 사용한다")
    void execute_MultipleValidCoupons_Success() {
        // given - 고객이 장바구니에서 보유 쿠폰들을 적용하는 상황
        Long customerId = 1L;
        List<Long> couponHistoryIds = List.of(1L, 2L);
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(customerId)
                .name("고객")
                .build();
        
        Order customerOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .userId(customerId)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        CouponHistory firstCoupon = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(1L)
                .userId(customerId)
                .couponId(1L)
                .build();
        CouponHistory secondCoupon = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(2L)
                .userId(customerId)
                .couponId(2L)
                .build();
        
        when(userRepositoryPort.findById(customerId)).thenReturn(Optional.of(customer));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(1L)).thenReturn(Optional.of(firstCoupon));
        when(couponHistoryRepositoryPort.findById(2L)).thenReturn(Optional.of(secondCoupon));
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        useCouponUseCase.execute(customerId, couponHistoryIds, customerOrder);

        // then
        verify(lockingPort, times(2)).acquireLock(anyString());
        verify(lockingPort, times(2)).releaseLock(anyString());
        
        ArgumentCaptor<CouponHistory> couponHistoryCaptor = ArgumentCaptor.forClass(CouponHistory.class);
        verify(couponHistoryRepositoryPort, times(2)).save(couponHistoryCaptor.capture());
        
        List<CouponHistory> usedCoupons = couponHistoryCaptor.getAllValues();
        assertThat(usedCoupons).allMatch(coupon -> coupon.getStatus() == CouponHistoryStatus.USED);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 쿠폰 사용 시도는 차단된다")
    void execute_NonExistentUser_ThrowsException() {
        // given - 탈퇴했거나 존재하지 않는 사용자의 쿠폰 사용 시도
        Long invalidUserId = 999L;
        List<Long> couponHistoryIds = List.of(1L);
        Order order = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        when(userRepositoryPort.findById(invalidUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(invalidUserId, couponHistoryIds, order))
                .isInstanceOf(UserException.NotFound.class);
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 사용 시도는 차단된다")
    void execute_NonExistentCoupon_ThrowsException() {
        // given - 만료되거나 삭제된 쿠폰 사용 시도
        Long customerId = 1L;
        List<Long> invalidCouponIds = List.of(999L);
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(customerId)
                .build();
        
        Order customerOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .build();
        
        when(userRepositoryPort.findById(customerId)).thenReturn(Optional.of(customer));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(customerId, invalidCouponIds, customerOrder))
                .isInstanceOf(CouponException.NotFound.class);
                
        verify(lockingPort).releaseLock("coupon-use-999");
    }

    @Test
    @DisplayName("다른 고객의 쿠폰을 사용하려는 시도는 차단된다")
    void execute_OtherCustomersCoupon_ThrowsException() {
        // given - 다른 고객이 소유한 쿠폰을 사용하려는 시도
        Long customerId = 1L;
        Long otherCustomerId = 2L;
        List<Long> othersCouponIds = List.of(1L);
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(customerId)
                .name("고객1")
                .build();
        
        Order customerOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .build();
        
        CouponHistory otherCustomerCoupon = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(1L)
                .userId(otherCustomerId) // 다른 고객 소유
                .couponId(1L)
                .build();
        
        when(userRepositoryPort.findById(customerId)).thenReturn(Optional.of(customer));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(1L)).thenReturn(Optional.of(otherCustomerCoupon));

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(customerId, othersCouponIds, customerOrder))
                .isInstanceOf(CouponException.NotFound.class);
                
        verify(lockingPort).releaseLock("coupon-use-1");
    }

    @Test
    @DisplayName("쿠폰 사용 중 동시성 충돌 시 안전하게 처리된다")
    void execute_ConcurrencyConflict_ThrowsException() {
        // given - 동시에 같은 쿠폰을 사용하려는 상황 (중복 클릭 등)
        Long customerId = 1L;
        List<Long> couponHistoryIds = List.of(1L);
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(customerId)
                .build();
        
        Order customerOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .build();
        
        when(userRepositoryPort.findById(customerId)).thenReturn(Optional.of(customer));
        when(lockingPort.acquireLock(anyString())).thenReturn(false); // 락 획득 실패

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(customerId, couponHistoryIds, customerOrder))
                .isInstanceOf(CommonException.ConcurrencyConflict.class);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidInputs")
    @DisplayName("잘못된 입력값으로 쿠폰 사용 시 예외가 발생한다")
    void execute_InvalidInputs_ThrowsException(Long userId, List<Long> couponHistoryIds, Order order, String expectedMessage) {
        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    @DisplayName("중복된 쿠폰 ID로 사용 시도는 차단된다")
    void execute_DuplicateCouponIds_ThrowsException() {
        // given - 같은 쿠폰을 중복으로 사용하려는 시도
        Long customerId = 1L;
        List<Long> duplicateCouponIds = List.of(1L, 1L); // 중복
        Order customerOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .build();

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(customerId, duplicateCouponIds, customerOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate coupon history IDs found");
    }

    @Test
    @DisplayName("여러 쿠폰을 순차적으로 안전하게 사용한다")
    void execute_MultipleCouponsSequentially_Success() {
        // given - 고객이 여러 할인 쿠폰을 함께 적용하는 상황
        Long customerId = 1L;
        List<Long> multipleCouponIds = List.of(3L, 1L, 2L); // 정렬되어 처리됨
        
        User customer = TestBuilder.UserBuilder.defaultUser()
                .id(customerId)
                .name("고객")
                .build();
        
        Order customerOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .totalAmount(new BigDecimal("100000"))
                .build();
        
        CouponHistory coupon1 = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(1L)
                .userId(customerId)
                .couponId(1L)
                .build();
        CouponHistory coupon2 = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(2L)
                .userId(customerId)
                .couponId(2L)
                .build();
        CouponHistory coupon3 = TestBuilder.CouponHistoryBuilder.issuedCouponHistory()
                .id(3L)
                .userId(customerId)
                .couponId(3L)
                .build();
        
        when(userRepositoryPort.findById(customerId)).thenReturn(Optional.of(customer));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponHistoryRepositoryPort.findById(2L)).thenReturn(Optional.of(coupon2));
        when(couponHistoryRepositoryPort.findById(3L)).thenReturn(Optional.of(coupon3));
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        useCouponUseCase.execute(customerId, multipleCouponIds, customerOrder);

        // then
        verify(lockingPort).acquireLock("coupon-use-1");
        verify(lockingPort).acquireLock("coupon-use-2");
        verify(lockingPort).acquireLock("coupon-use-3");
        verify(lockingPort, times(3)).releaseLock(anyString());
        verify(couponHistoryRepositoryPort, times(3)).save(any(CouponHistory.class));
    }

    static Stream<Arguments> provideInvalidInputs() {
        Order validOrder = TestBuilder.OrderBuilder.pendingOrder()
                .id(1L)
                .totalAmount(new BigDecimal("100000"))
                .build();
        return Stream.of(
                Arguments.of(null, List.of(1L), validOrder, "User ID cannot be null"),
                Arguments.of(1L, null, validOrder, "Coupon history IDs cannot be null or empty"),
                Arguments.of(1L, List.of(), validOrder, "Coupon history IDs cannot be null or empty"),
                Arguments.of(1L, List.of(1L), null, "Order cannot be null")
        );
    }
}