package kr.hhplus.be.server.unit.service.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.service.BalanceService;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * BalanceService.getBalance 메서드 테스트
 * 
 * Why: 잔액 조회 서비스가 고객의 잔액 정보 요구사항을 올바르게 처리하는지 검증
 * How: 실제 고객의 잔액 조회 시나리오를 반영한 서비스 레이어 테스트로 구성
 */
@DisplayName("잔액 조회 서비스")
class GetBalanceTest {

    @Mock
    private GetBalanceUseCase getBalanceUseCase;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    private BalanceService balanceService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        balanceService = new BalanceService(null, getBalanceUseCase, lockingPort, userRepositoryPort);
    }

    @Test
    @DisplayName("고객이 자신의 잔액 정보를 성공적으로 조회한다")
    void getBalance_Success() {
        // given - 고객이 마이페이지에서 잔액 확인하는 상황
        Long customerId = 1L;
        Balance customerBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(customerId)
                .amount(new BigDecimal("100000"))
                .build();
        
        when(userRepositoryPort.existsById(customerId)).thenReturn(true);
        when(getBalanceUseCase.execute(customerId)).thenReturn(java.util.Optional.of(customerBalance));
        
        // when
        Balance result = balanceService.getBalance(customerId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(customerId);
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("100000"));
        verify(userRepositoryPort).existsById(customerId);
        verify(getBalanceUseCase).execute(customerId);
    }
    
    @Test     
    @DisplayName("존재하지 않는 사용자의 잔액 조회 시 예외가 발생한다")
    void getBalance_UserNotFound() {
        // given - 탈퇴했거나 존재하지 않는 사용자의 잔액 조회 시도
        Long invalidUserId = 999L;
        
        when(userRepositoryPort.existsById(invalidUserId)).thenReturn(false);
        
        // when & then
        assertThatThrownBy(() -> balanceService.getBalance(invalidUserId))
            .isInstanceOf(UserException.NotFound.class);
        verify(userRepositoryPort).existsById(invalidUserId);
        verify(getBalanceUseCase, never()).execute(any());
    }
}