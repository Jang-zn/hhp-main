package kr.hhplus.be.server.unit.usecase.balance;

import kr.hhplus.be.server.TestConstants;
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
    private BalanceRepositoryPort balanceRepositoryPort;
    
    private ChargeBalanceUseCase chargeBalanceUseCase;
    
    private User testUser;
    private Balance testBalance;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceUseCase = new ChargeBalanceUseCase(balanceRepositoryPort);
        
        testUser = User.builder()
            .id(1L)
            .name(TestConstants.TEST_USER_NAME)
            .build();
            
        testBalance = Balance.builder()
            .id(1L)
            .userId(testUser.getId())
            .amount(TestConstants.DEFAULT_ORDER_AMOUNT)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("성공 - 기존 잔액에 충전")
    void execute_ExistingBalance_Success() {
        // given
        BigDecimal chargeAmount = TestConstants.DEFAULT_CHARGE_AMOUNT;
        BigDecimal expectedAmount = new BigDecimal("150000");
        
        when(balanceRepositoryPort.findByUserId(testUser.getId())).thenReturn(Optional.of(testBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Balance result = chargeBalanceUseCase.execute(testUser.getId(), chargeAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(expectedAmount);
        assertThat(result.getUserId()).isEqualTo(testUser.getId());
        
        verify(balanceRepositoryPort).findByUserId(testUser.getId());
        verify(balanceRepositoryPort).save(testBalance);
    }
    
    @Test
    @DisplayName("성공 - 신규 잔액 생성")
    void execute_NewBalance_Success() {
        // given
        BigDecimal chargeAmount = TestConstants.DEFAULT_CHARGE_AMOUNT;
        
        when(balanceRepositoryPort.findByUserId(testUser.getId())).thenReturn(Optional.empty());
        when(balanceRepositoryPort.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        Balance result = chargeBalanceUseCase.execute(testUser.getId(), chargeAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(chargeAmount);
        assertThat(result.getUserId()).isEqualTo(testUser.getId());
        
        verify(balanceRepositoryPort).findByUserId(testUser.getId());
        verify(balanceRepositoryPort).save(any(Balance.class));
    }
    
    
    @Test
    @DisplayName("실패 - 잘못된 충전 금액 (음수)")
    void execute_NegativeAmount() {
        // given
        BigDecimal chargeAmount = new BigDecimal("-10000");
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(testUser.getId(), chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 잘못된 충전 금액 (0)")
    void execute_ZeroAmount() {
        // given
        BigDecimal chargeAmount = BigDecimal.ZERO;
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(testUser.getId(), chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - null 파라미터")
    void execute_NullParameters() {
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(null, new BigDecimal("10000")))
            .isInstanceOf(UserException.UserIdCannotBeNull.class);
            
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(testUser.getId(), null))
            .isInstanceOf(BalanceException.InvalidAmount.class);
    }
    
    @Test
    @DisplayName("실패 - 잘못된 사용자 ID (0 또는 음수)")
    void execute_InvalidUserId() {
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(0L, new BigDecimal("10000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UserId must be positive");
            
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(-1L, new BigDecimal("10000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UserId must be positive");
            
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 최대 충전 한도 초과")
    void execute_ExceedsMaxAmount() {
        // given
        BigDecimal chargeAmount = new BigDecimal("2000000"); // 100만원 초과
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(testUser.getId(), chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
    
    @Test
    @DisplayName("실패 - 최소 충전 금액 미만")
    void execute_BelowMinAmount() {
        // given
        BigDecimal chargeAmount = new BigDecimal("500"); // 1000원 미만
        
        // when & then
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(testUser.getId(), chargeAmount))
            .isInstanceOf(BalanceException.InvalidAmount.class);
            
        verify(balanceRepositoryPort, never()).findByUserId(any());
        verify(balanceRepositoryPort, never()).save(any());
    }
}