package kr.hhplus.be.server.unit.controller;

import kr.hhplus.be.server.api.controller.BalanceController;
import kr.hhplus.be.server.api.dto.request.BalanceRequest;
import kr.hhplus.be.server.api.dto.response.BalanceResponse;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.GetBalanceUseCase;
import kr.hhplus.be.server.domain.exception.*;
import kr.hhplus.be.server.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceController 단위 테스트")
class BalanceControllerTest {

    private BalanceController balanceController;
    
    @Mock
    private ChargeBalanceUseCase chargeBalanceUseCase;
    
    @Mock
    private GetBalanceUseCase getBalanceUseCase;



    @BeforeEach
    void setUp() {
        balanceController = new BalanceController(chargeBalanceUseCase, getBalanceUseCase);
    }

    @Nested
    @DisplayName("잔액 충전 테스트")
    class ChargeBalanceTests {
        
        static Stream<Arguments> provideChargeData() {
            return Stream.of(
                    Arguments.of(1L, "10000"),
                    Arguments.of(2L, "50000"),
                    Arguments.of(3L, "100000")
            );
        }

        static Stream<Arguments> provideInvalidChargeData() {
            return Stream.of(
                    Arguments.of("최소 금액 미만", 1L, "500", BalanceException.InvalidAmount.class),
                    Arguments.of("최대 금액 초과", 1L, "2000000", BalanceException.InvalidAmount.class)
            );
        }
        
        @Test
        @DisplayName("성공케이스: 정상 잔액 충전")
        void chargeBalance_Success() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            BalanceRequest request = new BalanceRequest(userId, chargeAmount);

            
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Balance balance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("150000"))
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            when(chargeBalanceUseCase.execute(userId, chargeAmount)).thenReturn(balance);

            // when
            BalanceResponse response = balanceController.chargeBalance(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.amount()).isEqualTo(new BigDecimal("150000"));
            assertThat(response.updatedAt()).isNotNull();
        }

        @ParameterizedTest
        @MethodSource("provideChargeData")
        @DisplayName("성공케이스: 다양한 충전 금액으로 잔액 충전")
        void chargeBalance_WithDifferentAmounts(Long userId, String chargeAmount) {
            // given
            BalanceRequest request = new BalanceRequest(userId, new BigDecimal(chargeAmount));

            
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Balance balance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal(chargeAmount))
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            when(chargeBalanceUseCase.execute(userId, new BigDecimal(chargeAmount))).thenReturn(balance);

            // when
            BalanceResponse response = balanceController.chargeBalance(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.amount()).isEqualTo(new BigDecimal(chargeAmount));
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 잔액 충전")
        void chargeBalance_UserNotFound() {
            // given
            Long userId = 999L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            BalanceRequest request = new BalanceRequest(userId, chargeAmount);

            
            when(chargeBalanceUseCase.execute(userId, chargeAmount))
                    .thenThrow(new UserException.InvalidUser());

            // when & then
            assertThatThrownBy(() -> balanceController.chargeBalance(request))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }

        @Test
        @DisplayName("실패케이스: 비정상 충전 금액")
        void chargeBalance_InvalidAmount() {
            // given
            Long userId = 1L;
            BigDecimal invalidAmount = new BigDecimal("-10000");
            BalanceRequest request = new BalanceRequest(userId, invalidAmount);

            // when & then
            assertThatThrownBy(() -> balanceController.chargeBalance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(ErrorCode.NEGATIVE_AMOUNT.getMessage());
        }

        @Test
        @DisplayName("실패케이스: null ID와 금액으로 잔액 충전")
        void chargeBalance_WithNullFields() {
            // given
            BalanceRequest request = new BalanceRequest(null, null);

            // when & then
            assertThatThrownBy(() -> balanceController.chargeBalance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }

        @Test
        @DisplayName("실패케이스: null 요청으로 잔액 충전")
        void chargeBalance_WithNullRequest() {
            // when & then
            assertThatThrownBy(() -> balanceController.chargeBalance(null))
                    .isInstanceOf(CommonException.InvalidRequest.class)
                    .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
        }

        @ParameterizedTest
        @MethodSource("provideInvalidChargeData")
        @DisplayName("실패케이스: 비정상 충전 데이터")
        void chargeBalance_WithInvalidData(String description, Long userId, String amount, Class<? extends Exception> expectedException) {
            // given
            BalanceRequest request = new BalanceRequest(userId, new BigDecimal(amount));

            
            when(chargeBalanceUseCase.execute(userId, new BigDecimal(amount)))
                    .thenThrow(expectedException);

            // when & then
            assertThatThrownBy(() -> balanceController.chargeBalance(request))
                    .isInstanceOf(expectedException);
        }
    }

    @Nested
    @DisplayName("잔액 조회 테스트")
    class GetBalanceTests {
        
        static Stream<Arguments> provideUserIds() {
            return Stream.of(
                    Arguments.of(1L),
                    Arguments.of(100L),
                    Arguments.of(999L)
            );
        }

        static Stream<Arguments> provideInvalidUserIds() {
            return Stream.of(
                    Arguments.of(-1L),
                    Arguments.of(0L),
                    Arguments.of(Long.MAX_VALUE)
            );
        }
        
        @Test
        @DisplayName("성공케이스: 정상 잔액 조회")
        void getBalance_Success() {
            // given
            Long userId = 1L;
            
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Balance balance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("100000"))
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            when(getBalanceUseCase.execute(userId)).thenReturn(Optional.of(balance));

            // when
            BalanceResponse response = balanceController.getBalance(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.amount()).isEqualTo(new BigDecimal("100000"));
            assertThat(response.updatedAt()).isNotNull();
        }

        @ParameterizedTest
        @MethodSource("provideUserIds")
        @DisplayName("성공케이스: 다양한 사용자 ID로 잔액 조회")
        void getBalance_WithDifferentUserIds(Long userId) {
            // given
            User user = User.builder().id(userId).name("테스트 사용자").build();
            Balance balance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("50000"))
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            when(getBalanceUseCase.execute(userId)).thenReturn(Optional.of(balance));
            
            // when
            BalanceResponse response = balanceController.getBalance(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.amount()).isNotNull();
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 잔액 조회")
        void getBalance_UserNotFound() {
            // given
            Long userId = 999L;
            
            when(getBalanceUseCase.execute(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> balanceController.getBalance(userId))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }

        @Test
        @DisplayName("실패케이스: null 사용자 ID로 잔액 조회")
        void getBalance_WithNullUserId() {
            // when & then
            assertThatThrownBy(() -> balanceController.getBalance(null))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }

        @ParameterizedTest
        @MethodSource("provideInvalidUserIds")
        @DisplayName("실패케이스: 비정상 사용자 ID로 잔액 조회")
        void getBalance_WithInvalidUserIds(Long invalidUserId) {
            // given
            when(getBalanceUseCase.execute(invalidUserId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> balanceController.getBalance(invalidUserId))
                    .isInstanceOf(UserException.InvalidUser.class)
                    .hasMessage(ErrorCode.INVALID_USER_ID.getMessage());
        }
    }
}