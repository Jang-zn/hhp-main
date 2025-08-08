package kr.hhplus.be.server.unit.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.exception.BalanceException;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static kr.hhplus.be.server.util.TestAssertions.BalanceAssertions;

/**
 * 잔액 충전 비즈니스 로직 테스트
 * 
 * Why: 잔액 충전은 금융 서비스의 핵심으로 정확한 금액 처리와 예외 상황 대응이 중요
 * How: 다양한 충전 시나리오와 예외 케이스를 통해 비즈니스 규칙 준수 여부를 검증
 */
@DisplayName("고객 잔액 충전 서비스")
class ChargeBalanceUseCaseTest {

    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    private ChargeBalanceUseCase chargeBalanceUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceUseCase = new ChargeBalanceUseCase(balanceRepositoryPort);
    }

    @Test
    @DisplayName("기존 고객이 잔액을 충전하면 정확히 누적된다")
    void accuratelyAccumulatesBalanceForExistingCustomer() {
        // Given - 기존 잔액이 있는 고객이 추가 충전하는 상황
        // Why: 기존 잔액과의 정확한 합산으로 고객 자금의 정확성 보장
        Long customerId = 1L;
        BigDecimal existingBalance = BigDecimal.valueOf(50000); // 기존 5만원
        BigDecimal chargeAmount = BigDecimal.valueOf(30000);    // 3만원 충전
        BigDecimal expectedTotal = BigDecimal.valueOf(80000);   // 총 8만원 예상
        
        Balance currentBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(customerId)
            .amount(existingBalance)
            .build();
            
        Balance updatedBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(customerId)
            .amount(expectedTotal)
            .build();

        // Mock 설정: 기존 잔액 조회 및 업데이트된 잔액 저장
        when(balanceRepositoryPort.findByUserId(customerId)).thenReturn(Optional.of(currentBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(updatedBalance);

        // When - 고객이 3만원 충전
        Balance result = chargeBalanceUseCase.execute(customerId, chargeAmount);

        // Then - 기존 잔액에 충전 금액이 정확히 누적됨
        BalanceAssertions.assertCharged(result, existingBalance, chargeAmount);
        assertThat(result.getAmount())
            .as("충전 후 총 잔액이 정확해야 함")
            .isEqualByComparingTo(expectedTotal);
            
        verify(balanceRepositoryPort).save(any(Balance.class));
    }

    @Test
    @DisplayName("신규 고객의 첫 충전 시 새로운 잔액을 생성한다")
    void createsNewBalanceForFirstTimeCustomerCharge() {
        // Given - 잔액 정보가 없는 신규 고객의 첫 충전
        // Why: 신규 가입 고객도 원활하게 서비스 이용할 수 있도록 자동 잔액 생성
        Long newCustomerId = 1L;
        BigDecimal firstChargeAmount = BigDecimal.valueOf(100000); // 첫 충전 10만원
        
        Balance newBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(newCustomerId)
            .amount(firstChargeAmount)
            .build();

        // Mock 설정: 기존 잔액 없음, 새 잔액 생성
        when(balanceRepositoryPort.findByUserId(newCustomerId)).thenReturn(Optional.empty());
        when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(newBalance);

        // When - 신규 고객이 첫 충전
        Balance result = chargeBalanceUseCase.execute(newCustomerId, firstChargeAmount);

        // Then - 신규 잔액이 정확한 금액으로 생성됨
        assertThat(result)
            .as("신규 고객에게 잔액이 생성되어야 함")
            .isNotNull();
            
        assertThat(result.getAmount())
            .as("첫 충전 금액이 정확히 설정되어야 함")
            .isEqualByComparingTo(firstChargeAmount);
            
        assertThat(result.getUserId())
            .as("올바른 고객 ID로 잔액이 생성되어야 함")
            .isEqualTo(newCustomerId);
    }

    @Test
    @DisplayName("최소 충전 금액 미만 시 충전을 거부한다")
    void rejectsChargeAmountBelowMinimum() {
        // Given - 최소 충전 금액보다 적은 금액으로 충전 시도
        // Why: 소액 충전으로 인한 수수료 손실 방지와 비즈니스 정책 준수
        Long customerId = 1L;
        BigDecimal tooSmallAmount = BigDecimal.valueOf(500); // 최소 금액(1000원) 미만

        // When & Then - 최소 충전 금액 미만으로 충전 시도 시 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(customerId, tooSmallAmount))
            .as("최소 충전 금액 미만은 거부되어야 함")
            .isInstanceOf(BalanceException.InvalidAmount.class);
    }

    @Test
    @DisplayName("최대 충전 한도 초과 시 충전을 거부한다")
    void rejectsChargeAmountExceedingMaximum() {
        // Given - 최대 충전 한도를 초과하는 금액으로 충전 시도
        // Why: 자금세탁 방지 및 리스크 관리를 위한 한도 제한
        Long customerId = 1L;
        BigDecimal excessiveAmount = BigDecimal.valueOf(1500000); // 최대 한도(100만원) 초과

        // When & Then - 최대 충전 한도 초과 시 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(customerId, excessiveAmount))
            .as("최대 충전 한도 초과는 거부되어야 함")
            .isInstanceOf(BalanceException.InvalidAmount.class);
    }

    @Test
    @DisplayName("유효하지 않은 고객 ID에 대해 충전을 거부한다")
    void rejectsChargeForInvalidCustomerId() {
        // Given - 잘못된 고객 ID로 충전 시도 (null, 음수, 0 등)
        // Why: 유효하지 않은 고객 정보로 인한 시스템 오류 방지
        Long invalidCustomerId = null;
        Long negativeCustomerId = -1L;
        Long zeroCustomerId = 0L;
        BigDecimal validAmount = BigDecimal.valueOf(50000);

        // When & Then - null 고객 ID 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(invalidCustomerId, validAmount))
            .as("null 고객 ID는 거부되어야 함")
            .isInstanceOf(UserException.UserIdCannotBeNull.class);

        // When & Then - 음수 고객 ID 거부  
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(negativeCustomerId, validAmount))
            .as("음수 고객 ID는 거부되어야 함")
            .isInstanceOf(IllegalArgumentException.class);

        // When & Then - 0 고객 ID 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(zeroCustomerId, validAmount))
            .as("0 고객 ID는 거부되어야 함")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잘못된 충전 금액에 대해 명확한 오류를 제공한다")
    void providesCleerErrorForInvalidChargeAmount() {
        // Given - 다양한 형태의 잘못된 충전 금액
        // Why: 사용자 입력 오류에 대한 명확한 피드백으로 사용자 경험 개선
        Long validCustomerId = 1L;
        BigDecimal nullAmount = null;
        BigDecimal zeroAmount = BigDecimal.ZERO;
        BigDecimal negativeAmount = BigDecimal.valueOf(-1000);

        // When & Then - null 충전 금액 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(validCustomerId, nullAmount))
            .as("null 충전 금액은 거부되어야 함")
            .isInstanceOf(BalanceException.InvalidAmount.class);

        // When & Then - 0원 충전 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(validCustomerId, zeroAmount))
            .as("0원 충전은 거부되어야 함")
            .isInstanceOf(BalanceException.InvalidAmount.class);

        // When & Then - 음수 충전 금액 거부
        assertThatThrownBy(() -> chargeBalanceUseCase.execute(validCustomerId, negativeAmount))
            .as("음수 충전 금액은 거부되어야 함")
            .isInstanceOf(BalanceException.InvalidAmount.class);
    }

    @Test
    @DisplayName("충전 한도 내에서 대용량 충전을 안전하게 처리한다")
    void safelyHandlesLargeChargeWithinLimit() {
        // Given - 최대 한도 내에서의 대용량 충전 (기업 고객 등)
        // Why: 정당한 대용량 충전에 대한 안전한 처리와 정확한 금액 계산
        Long enterpriseCustomerId = 1L;
        BigDecimal largeAmount = BigDecimal.valueOf(1000000); // 최대 한도 100만원
        BigDecimal existingBalance = BigDecimal.valueOf(2000000); // 기존 200만원
        BigDecimal expectedTotal = BigDecimal.valueOf(3000000); // 총 300만원
        
        Balance currentBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(enterpriseCustomerId)
            .amount(existingBalance)
            .build();
            
        Balance updatedBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(enterpriseCustomerId)
            .amount(expectedTotal)
            .build();

        // Mock 설정
        when(balanceRepositoryPort.findByUserId(enterpriseCustomerId)).thenReturn(Optional.of(currentBalance));
        when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(updatedBalance);

        // When - 최대 한도 내에서 대용량 충전
        Balance result = chargeBalanceUseCase.execute(enterpriseCustomerId, largeAmount);

        // Then - 대용량 금액도 정확히 처리됨
        assertThat(result.getAmount())
            .as("대용량 충전도 정확한 계산 결과를 보장해야 함")
            .isEqualByComparingTo(expectedTotal);
            
        BalanceAssertions.assertCharged(result, existingBalance, largeAmount);
    }
}