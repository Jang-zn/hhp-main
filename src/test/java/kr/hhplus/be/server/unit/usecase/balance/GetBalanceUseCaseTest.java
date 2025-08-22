package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GetBalanceUseCase 단위 테스트")
class GetBalanceUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;

    @Mock
    private CachePort cachePort;

    @Mock
    private KeyGenerator keyGenerator;


    private GetBalanceUseCase getBalanceUseCase;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getBalanceUseCase = new GetBalanceUseCase(
                userRepositoryPort, balanceRepositoryPort, cachePort, keyGenerator
        );
    }

    @Nested
    @DisplayName("성공 케이스 테스트")
    class SuccessTests {
        
        static Stream<Arguments> provideUserData() {
            return Stream.of(
                    Arguments.of(1L, "홍길동", "50000"),
                    Arguments.of(2L, "김철수", "100000"),
                    Arguments.of(3L, "이영희", "75000")
            );
        }

        @Test
        @DisplayName("성공케이스: DB에서 정상 잔액 조회")
        void getBalance_Success_FromDB() {
            // given
            Long userId = 1L;
            String cacheKey = "balance:" + userId;
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Balance balance = Balance.builder().userId(userId).amount(new BigDecimal("100000")).build();

                        when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(balance));

            // when
            Optional<Balance> result = getBalanceUseCase.execute(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("100000"));

            verify(userRepositoryPort).existsById(userId);
            verify(balanceRepositoryPort).findByUserId(userId);
        }

        @Test
        @DisplayName("성공케이스: Cache에서 정상 잔액 조회")
        void getBalance_Success_FromCache() {
            // given
            Long userId = 1L;
            String cacheKey = "balance:" + userId;
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Balance cachedBalance = Balance.builder().userId(userId).amount(new BigDecimal("123456")).build();

            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(cachedBalance));

            // when
            Optional<Balance> result = getBalanceUseCase.execute(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("123456"));

            verify(userRepositoryPort).existsById(userId);
            verify(balanceRepositoryPort).findByUserId(userId);
        }

        @ParameterizedTest
        @MethodSource("provideUserData")
        @DisplayName("성공케이스: 다양한 사용자 잔액 조회 (DB)")
        void getBalance_WithDifferentUsers(Long userId, String userName, String amount) {
            // given
            String cacheKey = "balance:" + userId;
            User user = User.builder().id(userId).name(userName).build();
            Balance balance = Balance.builder().userId(userId).amount(new BigDecimal(amount)).build();

            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(balance));

            // when
            Optional<Balance> result = getBalanceUseCase.execute(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getAmount()).isEqualTo(new BigDecimal(amount));
        }

        @Test
        @DisplayName("성공케이스: 음수 잔액을 가진 사용자")
        void getBalance_WithNegativeBalance() {
            // given
            Long userId = 1L;
            User user = User.builder().id(userId).name("음수 잔액 사용자").build();
            Balance negativeBalance = Balance.builder().userId(userId).amount(new BigDecimal("-50000")).build();

            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(negativeBalance));

            // when
            Optional<Balance> result = getBalanceUseCase.execute(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("-50000"));
        }

        @Test
        @DisplayName("성공케이스: 매우 큰 잔액을 가진 사용자")
        void getBalance_WithLargeBalance() {
            // given
            Long userId = 1L;
            User user = User.builder().id(userId).name("부자 사용자").build();
            Balance largeBalance = Balance.builder().userId(userId).amount(new BigDecimal("999999999999")).build();

            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(largeBalance));

            // when
            Optional<Balance> result = getBalanceUseCase.execute(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("999999999999"));
        }
    }

    @Nested
    @DisplayName("실패 케이스 테스트")
    class FailureTests {
        
        static Stream<Arguments> provideInvalidUserIds() {
            return Stream.of(
                    Arguments.of(-1L),
                    Arguments.of(0L)
            );
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 잔액 조회")
        void getBalance_UserNotFound() {
            // given
            Long userId = 999L;
            when(userRepositoryPort.existsById(userId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> getBalanceUseCase.execute(userId))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());

            verify(balanceRepositoryPort, never()).findByUserId(anyLong());
        }

        @Test
        @DisplayName("실패케이스: null 사용자 ID로 잔액 조회")
        void getBalance_WithNullUserId() {
            // given
            Long userId = null;

            // when & then
            assertThatThrownBy(() -> getBalanceUseCase.execute(userId))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }

        @Test
        @DisplayName("실패케이스: 잔액이 존재하지 않는 사용자")
        void getBalance_NoBalance() {
            // given
            Long userId = 1L;
            User user = User.builder().id(userId).name("잔액 없는 사용자").build();

            when(userRepositoryPort.existsById(userId)).thenReturn(true);
            when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.empty());

            // when
            Optional<Balance> result = getBalanceUseCase.execute(userId);

            // then
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("provideInvalidUserIds")
        @DisplayName("실패케이스: 다양한 비정상 사용자 ID로 잔액 조회")
        void getBalance_WithInvalidUserIds(Long invalidUserId) {
            // given
            when(userRepositoryPort.existsById(invalidUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> getBalanceUseCase.execute(invalidUserId))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }
    }
}
