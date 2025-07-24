package kr.hhplus.be.server.unit.usecase.coupon;

import kr.hhplus.be.server.domain.entity.CouponHistory;
import kr.hhplus.be.server.domain.entity.Order;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.entity.Coupon;
import kr.hhplus.be.server.domain.entity.OrderStatus;
import kr.hhplus.be.server.domain.enums.CouponHistoryStatus;
import kr.hhplus.be.server.domain.enums.CouponStatus;
import kr.hhplus.be.server.domain.exception.CouponException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.domain.exception.CommonException;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.CouponHistoryRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("UseCouponUseCase 단위 테스트")
class UseCouponUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private CouponHistoryRepositoryPort couponHistoryRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;

    private UseCouponUseCase useCouponUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCouponUseCase = new UseCouponUseCase(userRepositoryPort, couponHistoryRepositoryPort, lockingPort);
    }

    @Test
    @DisplayName("쿠폰 사용 성공")
    void useCoupon_Success() {
        // given
        Long userId = 1L;
        List<Long> couponHistoryIds = List.of(1L, 2L);
        
        User user = User.builder()
                .id(userId)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(1L)
                .build();
        
        CouponHistory couponHistory1 = createCouponHistory(1L, user, "COUPON1");
        CouponHistory couponHistory2 = createCouponHistory(2L, user, "COUPON2");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(1L)).thenReturn(Optional.of(couponHistory1));
        when(couponHistoryRepositoryPort.findById(2L)).thenReturn(Optional.of(couponHistory2));
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        useCouponUseCase.execute(userId, couponHistoryIds, order);

        // then
        verify(lockingPort, times(2)).acquireLock(anyString());
        verify(lockingPort, times(2)).releaseLock(anyString());
        
        ArgumentCaptor<CouponHistory> couponHistoryCaptor = ArgumentCaptor.forClass(CouponHistory.class);
        verify(couponHistoryRepositoryPort, times(2)).save(couponHistoryCaptor.capture());
        
        List<CouponHistory> savedCouponHistories = couponHistoryCaptor.getAllValues();
        savedCouponHistories.forEach(savedHistory -> {
            assertThat(savedHistory.getStatus()).isEqualTo(CouponHistoryStatus.USED);
        });
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 쿠폰 사용 시 예외 발생")
    void useCoupon_UserNotFound() {
        // given
        Long userId = 999L;
        List<Long> couponHistoryIds = List.of(1L);
        Order order = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(UserException.NotFound.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 히스토리로 사용 시 예외 발생")
    void useCoupon_CouponHistoryNotFound() {
        // given
        Long userId = 1L;
        List<Long> couponHistoryIds = List.of(999L);
        
        User user = User.builder()
                .id(userId)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(ErrorCode.COUPON_NOT_FOUND.getMessage());
                
        verify(lockingPort).releaseLock("coupon-use-999");
    }

    @Test
    @DisplayName("다른 사용자의 쿠폰 사용 시 예외 발생")
    void useCoupon_NotOwner() {
        // given
        Long userId = 1L;
        List<Long> couponHistoryIds = List.of(1L);
        
        User user = User.builder()
                .id(userId)
                .name("사용자1")
                .build();
        
        User otherUser = User.builder()
                .id(2L)
                .name("사용자2")
                .build();
        
        Order order = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();
        
        CouponHistory couponHistory = createCouponHistory(1L, otherUser, "OTHER_COUPON");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(1L)).thenReturn(Optional.of(couponHistory));

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(CouponException.NotFound.class)
                .hasMessage(ErrorCode.COUPON_NOT_FOUND.getMessage());
                
        verify(lockingPort).releaseLock("coupon-use-1");
    }

    @Test
    @DisplayName("락 획득 실패 시 예외 발생")
    void useCoupon_LockAcquisitionFailed() {
        // given
        Long userId = 1L;
        List<Long> couponHistoryIds = List.of(1L);
        
        User user = User.builder()
                .id(userId)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(lockingPort.acquireLock(anyString())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(CommonException.ConcurrencyConflict.class)
                .hasMessage(ErrorCode.CONCURRENCY_ERROR.getMessage());
    }

    @ParameterizedTest
    @MethodSource("provideInvalidInputs")
    @DisplayName("잘못된 입력값으로 쿠폰 사용 시 예외 발생")
    void useCoupon_WithInvalidInputs(Long userId, List<Long> couponHistoryIds, Order order, String expectedMessage) {
        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    @DisplayName("중복된 쿠폰 ID로 사용 시 예외 발생")
    void useCoupon_WithDuplicateIds() {
        // given
        Long userId = 1L;
        List<Long> couponHistoryIds = List.of(1L, 1L); // 중복
        Order order = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();

        // when & then
        assertThatThrownBy(() -> useCouponUseCase.execute(userId, couponHistoryIds, order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate coupon history IDs found");
    }

    @Test
    @DisplayName("여러 쿠폰을 순차적으로 사용")
    void useCoupon_MultipleCouppons() {
        // given
        Long userId = 1L;
        List<Long> couponHistoryIds = List.of(3L, 1L, 2L); // 정렬되어 처리됨
        
        User user = User.builder()
                .id(userId)
                .name("테스트 사용자")
                .build();
        
        Order order = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();
        
        CouponHistory couponHistory1 = createCouponHistory(1L, user, "COUPON1");
        CouponHistory couponHistory2 = createCouponHistory(2L, user, "COUPON2");
        CouponHistory couponHistory3 = createCouponHistory(3L, user, "COUPON3");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(lockingPort.acquireLock(anyString())).thenReturn(true);
        when(couponHistoryRepositoryPort.findById(1L)).thenReturn(Optional.of(couponHistory1));
        when(couponHistoryRepositoryPort.findById(2L)).thenReturn(Optional.of(couponHistory2));
        when(couponHistoryRepositoryPort.findById(3L)).thenReturn(Optional.of(couponHistory3));
        when(couponHistoryRepositoryPort.save(any(CouponHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        useCouponUseCase.execute(userId, couponHistoryIds, order);

        // then
        verify(lockingPort).acquireLock("coupon-use-1");
        verify(lockingPort).acquireLock("coupon-use-2");
        verify(lockingPort).acquireLock("coupon-use-3");
        verify(lockingPort, times(3)).releaseLock(anyString());
        verify(couponHistoryRepositoryPort, times(3)).save(any(CouponHistory.class));
    }

    private CouponHistory createCouponHistory(Long id, User user, String couponCode) {
        Coupon coupon = Coupon.builder()
                .id(id)
                .code(couponCode)
                .discountRate(new BigDecimal("0.10"))
                .maxIssuance(100)
                .issuedCount(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(CouponStatus.ACTIVE)
                .build();

        return CouponHistory.builder()
                .id(id)
                .user(user)
                .coupon(coupon)
                .issuedAt(LocalDateTime.now())
                .status(CouponHistoryStatus.ISSUED)
                .build();
    }

    private static Stream<Arguments> provideInvalidInputs() {
        Order validOrder = Order.builder()
                .id(1L)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .build();
        return Stream.of(
                Arguments.of(null, List.of(1L), validOrder, "User ID cannot be null"),
                Arguments.of(1L, null, validOrder, "Coupon history IDs cannot be null or empty"),
                Arguments.of(1L, List.of(), validOrder, "Coupon history IDs cannot be null or empty"),
                Arguments.of(1L, List.of(1L), null, "Order cannot be null")
        );
    }
}