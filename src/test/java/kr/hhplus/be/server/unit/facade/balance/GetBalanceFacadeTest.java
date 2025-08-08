package kr.hhplus.be.server.unit.facade.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.balance.GetBalanceFacade;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.util.TestBuilder;
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

/**
 * GetBalanceFacade 비즈니스 시나리오 테스트
 * 
 * Why: 잔액 조회 파사드가 고객의 잔액 정보 요구사항을 올바르게 처리하는지 검증
 * How: 실제 고객의 잔액 조회 시나리오를 반영한 파사드 레이어 테스트로 구성
 */
@DisplayName("잔액 조회 파사드 비즈니스 시나리오")
class GetBalanceFacadeTest {

    @Mock
    private GetBalanceUseCase getBalanceUseCase;
    
    private GetBalanceFacade getBalanceFacade;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getBalanceFacade = new GetBalanceFacade(getBalanceUseCase);
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
        
        when(getBalanceUseCase.execute(customerId)).thenReturn(Optional.of(customerBalance));
        
        // when
        Optional<Balance> result = getBalanceFacade.getBalance(customerId);
        
        // then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(customerId);
        assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("100000"));
        verify(getBalanceUseCase).execute(customerId);
    }
    
    @Test
    @DisplayName("아직 잔액을 설정하지 않은 신규 고객의 조회 시 빈 결과를 반환한다")
    void getBalance_NotFound() {
        // given - 아직 잔액이 설정되지 않은 신규 고객
        Long newCustomerId = 1L;
        
        when(getBalanceUseCase.execute(newCustomerId)).thenReturn(Optional.empty());
        
        // when
        Optional<Balance> result = getBalanceFacade.getBalance(newCustomerId);
        
        // then
        assertThat(result).isEmpty();
        verify(getBalanceUseCase).execute(newCustomerId);
    }
    
    @Test     
    @DisplayName("존재하지 않는 사용자의 잔액 조회 시 예외가 발생한다")
    void getBalance_UserNotFound() {
        // given - 탈퇴했거나 존재하지 않는 사용자의 잔액 조회 시도
        Long invalidUserId = 999L;
        
        when(getBalanceUseCase.execute(invalidUserId))
            .thenThrow(new UserException.InvalidUser());
        
        // when & then
        assertThatThrownBy(() -> getBalanceFacade.getBalance(invalidUserId))
            .isInstanceOf(UserException.InvalidUser.class);
        verify(getBalanceUseCase).execute(invalidUserId);
    }
}