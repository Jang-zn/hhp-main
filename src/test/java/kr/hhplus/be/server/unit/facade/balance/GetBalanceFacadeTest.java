package kr.hhplus.be.server.unit.facade.balance;

import kr.hhplus.be.server.domain.entity.*;
import kr.hhplus.be.server.domain.facade.balance.GetBalanceFacade;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@DisplayName("GetBalanceFacade 단위 테스트")
class GetBalanceFacadeTest {

    @Mock
    private GetBalanceUseCase getBalanceUseCase;
    
    private GetBalanceFacade getBalanceFacade;
    
    private User testUser;
    private Balance testBalance;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getBalanceFacade = new GetBalanceFacade(getBalanceUseCase);
        
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

    @Nested
    @DisplayName("잔액 조회")
    class GetBalance {
        
        @Test
        @DisplayName("성공 - 정상 잔액 조회")
        void getBalance_Success() {
            // given
            Long userId = 1L;
            
            when(getBalanceUseCase.execute(userId)).thenReturn(Optional.of(testBalance));
            
            // when
            Optional<Balance> result = getBalanceFacade.getBalance(userId);
            
            // then
            assertThat(result).isPresent();
            assertThat(result.get().getUser().getId()).isEqualTo(userId);
            assertThat(result.get().getAmount()).isEqualTo(new BigDecimal("100000"));
            
            verify(getBalanceUseCase).execute(userId);
        }
        
        @Test
        @DisplayName("성공 - 존재하지 않는 잔액")
        void getBalance_NotFound() {
            // given
            Long userId = 1L;
            
            when(getBalanceUseCase.execute(userId)).thenReturn(Optional.empty());
            
            // when
            Optional<Balance> result = getBalanceFacade.getBalance(userId);
            
            // then
            assertThat(result).isEmpty();
            
            verify(getBalanceUseCase).execute(userId);
        }
        
        @Test     
        @DisplayName("실패 - 존재하지 않는 사용자")
        void getBalance_UserNotFound() {
            // given
            Long userId = 999L;
            
            when(getBalanceUseCase.execute(userId))
                .thenThrow(new UserException.InvalidUser());
            
            // when & then
            assertThatThrownBy(() -> getBalanceFacade.getBalance(userId))
                .isInstanceOf(UserException.InvalidUser.class);
                
            verify(getBalanceUseCase).execute(userId);
        }
    }
}