package kr.hhplus.be.server.unit.usecase.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.port.storage.*;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChargeBalanceUseCase 단위 테스트")
class ChargeBalanceUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    private ChargeBalanceUseCase chargeBalanceUseCase;
    
    private User testUser;
    private Balance testBalance;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceUseCase = new ChargeBalanceUseCase(userRepositoryPort, balanceRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name("Test User")
            .build();
            
        testBalance = Balance.builder()
            .id(1L)
            .user(testUser)
            .amount(new BigDecimal("100000"))
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("성공 - 기존 잔액에 충전")
    void execute_ExistingBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        BigDecimal expectedAmount = new BigDecimal("150000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.of(testBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(expectedAmount);
        assertThat(result.getUser()).isEqualTo(testUser);
        
        verify(userRepositoryPort).findById(userId);
        verify(balanceRepositoryPort).findByUserId(userId);
        verify(balanceRepositoryPort).save(testBalance);
    }
    
    @Test
    @DisplayName("성공 - 신규 잔액 생성")
    void execute_NewBalance_Success() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceRepositoryPort.findByUserId(userId)).thenReturn(Optional.empty());
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(chargeAmount);
        assertThat(result.getUser()).isEqualTo(testUser);
        
        verify(userRepositoryPort).findById(userId);
        verify(balanceRepositoryPort).findByUserId(userId);
        verify(balanceRepositoryPort).save(any(Balance.class));
    }
    
    @Test
    @DisplayName("실패 - 존재하지 않는 사용자")
    void execute_UserNotFound() {
        // given
        Long userId = 999L;
        BigDecimal chargeAmount = new BigDecimal("50000");
        
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
            .isInstanceOf(UserException.NotFound.class);
            
        verify(userRepositoryPort).findById(userId);
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 잘못된 충전 금액 (음수)")
    void execute_NegativeAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("-10000");
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(userRepositoryPort, never()).findById(any());
    }
    
    @Test
    @DisplayName("실패 - 잘못된 충전 금액 (0)")
    void execute_ZeroAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = BigDecimal.ZERO;
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(userRepositoryPort, never()).findById(any());
    }
    
    @Test
    @DisplayName("실패 - null 파라미터")
    void execute_NullParameters() {
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(null, new BigDecimal("10000")))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(1L, null))
            .isInstanceOf(BalanceException.InvalidAmount.class);
    }
    
    @Test
    @DisplayName("실패 - 최대 충전 한도 초과")
    void execute_ExceedsMaxAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("2000000"); // 100만원 초과
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(userRepositoryPort, never()).findById(any());
    }
    
    @Test
    @DisplayName("실패 - 최소 충전 금액 미만")
    void execute_BelowMinAmount() {
        // given
        Long userId = 1L;
        BigDecimal chargeAmount = new BigDecimal("500"); // 1000원 미만
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(userRepositoryPort, never()).findById(any());
    }
}