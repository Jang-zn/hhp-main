package kr.hhplus.be.server.unit.usecase.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DeductBalanceUseCase 단위 테스트")
class DeductBalanceUseCaseTest {

    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    private DeductBalanceUseCase deductBalanceUseCase;
    
    private User testUser;
    private Balance testBalance;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        deductBalanceUseCase = new DeductBalanceUseCase(balanceRepositoryPort, userRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testBalance = Balance.builder()
            .id(1L)
            .user(testUser)
            .amount(new BigDecimal("1000000"))
            .build();
    }

    @Test
    @DisplayName("성공 - 잔액 차감")
    void execute_SufficientBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal deductAmount = new BigDecimal("50000");
        BigDecimal expectedRemainingAmount = new BigDecimal("950000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(testBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        BigDecimal result = deductBalanceUseCase.execute(userId, deductAmount);
        
        // then
        assertThat(result).isEqualTo(expectedRemainingAmount);
        verify(balanceRepositoryPort).save(testBalance);
        assertThat(testBalance.getAmount()).isEqualTo(expectedRemainingAmount);
    }
    
    @Test
    @DisplayName("실패 - 사용자를 찾을 수 없음")
    void execute_UserNotFound_ThrowsException() {
        // given
        Long userId = 999L;
        BigDecimal deductAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> deductBalanceUseCase.execute(userId, deductAmount))
            .isInstanceOf(UserException.UserNotFound.class);
            
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 잔액 정보를 찾을 수 없음")
    void execute_BalanceNotFound_ThrowsException() {
        // given
        Long userId = 1L;
        BigDecimal deductAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> deductBalanceUseCase.execute(userId, deductAmount))
            .isInstanceOf(BalanceException.BalanceNotFound.class);
            
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 잔액 부족")
    void execute_InsufficientBalance_ThrowsException() {
        // given
        Long userId = 1L;
        BigDecimal deductAmount = new BigDecimal("2000000"); // 잔액보다 큰 금액
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(testBalance));
        
        // when & then
        assertThatThrownBy(() -> deductBalanceUseCase.execute(userId, deductAmount))
            .isInstanceOf(BalanceException.InsufficientBalance.class);
            
        verify(balanceRepositoryPort, never()).save(any());
        // 잔액이 변경되지 않았는지 확인
        assertThat(testBalance.getAmount()).isEqualTo(new BigDecimal("1000000"));
    }
    
    @Test
    @DisplayName("실패 - 음수 금액")
    void execute_NegativeAmount_ThrowsException() {
        // given
        Long userId = 1L;
        BigDecimal deductAmount = new BigDecimal("-1000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(testBalance));
        
        // when & then
        assertThatThrownBy(() -> deductBalanceUseCase.execute(userId, deductAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(balanceRepositoryPort, never()).save(any());
    }
}