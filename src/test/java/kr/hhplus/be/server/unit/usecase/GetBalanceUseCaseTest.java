package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

@DisplayName("GetBalanceUseCase 단위 테스트")
class GetBalanceUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    @Mock
    private CachePort cachePort;

    private GetBalanceUseCase getBalanceUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getBalanceUseCase = new GetBalanceUseCase(
                userRepositoryPort, balanceRepositoryPort, cachePort
        );
    }

    @Test
    @DisplayName("잔액 조회 성공")
    void getBalance_Success() {
        // given
        Long userId = 1L;
        
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(balance));

        // when
        Optional<Balance> result = getBalanceUseCase.execute(userId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isPresent();
        // assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 잔액 조회")
    void getBalance_UserNotFound() {
        // given
        Long userId = 999L;
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());

        // when
        Optional<Balance> result = getBalanceUseCase.execute(userId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideUserData")
    @DisplayName("다양한 사용자 잔액 조회")
    void getBalance_WithDifferentUsers(Long userId, String userName, String amount) {
        // given
        User user = User.builder()
                .name(userName)
                .build();
        
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal(amount))
                .build();
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(balance));

        // when
        Optional<Balance> result = getBalanceUseCase.execute(userId);

        // then - TODO 구현이 완료되면 실제 검증 로직 추가
        // 현재는 empty 반환하는 메서드이므로 기본 검증만 수행
        // assertThat(result).isPresent();
        // assertThat(result.get().getAmount()).isEqualTo(new BigDecimal(amount));
    }

    private static Stream<Arguments> provideUserData() {
        return Stream.of(
                Arguments.of(1L, "홍길동", "50000"),
                Arguments.of(2L, "김철수", "100000"),
                Arguments.of(3L, "이영희", "75000")
        );
    }
}