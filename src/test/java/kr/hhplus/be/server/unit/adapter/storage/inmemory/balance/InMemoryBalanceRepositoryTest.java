package kr.hhplus.be.server.unit.adapter.storage.inmemory.balance;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryBalanceRepository;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.TestAssertions;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static kr.hhplus.be.server.util.TestAssertions.BalanceAssertions;

/**
 * InMemoryBalanceRepository 잔액 관리 테스트
 * 
 * Why: 메모리 기반 잔액 저장소가 동시성 환경에서 잔액 일관성을 올바르게 보장하는지 검증
 * How: 실제 비즈니스 시나리오를 중심으로 잔액 충전, 차감, 조회의 정확성을 확인
 */
@DisplayName("메모리 기반 잔액 관리")
class InMemoryBalanceRepositoryTest {

    private InMemoryBalanceRepository balanceRepository;

    @BeforeEach
    void setUp() {
        balanceRepository = new InMemoryBalanceRepository();
        balanceRepository.clear();
    }

    @Test
    @DisplayName("새로운 사용자의 잔액을 정확히 저장한다")
    void storesNewUserBalanceAccurately() {
        // Given - 신규 가입한 사용자의 초기 잔액
        // Why: 회원가입 시 0원으로 잔액이 초기화되는 비즈니스 규칙 검증
        Balance newUserBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(1L)
            .amount(BigDecimal.ZERO)
            .build();

        // When - 초기 잔액 저장
        Balance savedBalance = balanceRepository.save(newUserBalance);

        // Then - 정확한 초기 잔액으로 저장됨
        BalanceAssertions.assertSavedCorrectly(savedBalance, newUserBalance);
        assertThat(savedBalance.getId()).as("저장된 잔액은 고유 ID를 가져야 함").isNotNull();
    }

    @Test
    @DisplayName("사용자별 잔액을 정확히 조회한다")
    void retrievesUserBalanceAccurately() {
        // Given - 특정 잔액을 가진 사용자
        // Why: 결제 전 잔액 확인이나 잔액 조회 기능의 정확성 검증
        Long userId = 1L;
        Balance userBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(userId)
            .amount(BigDecimal.valueOf(50000))
            .build();
        balanceRepository.save(userBalance);

        // When - 사용자별 잔액 조회
        Optional<Balance> foundBalance = balanceRepository.findByUserId(userId);

        // Then - 정확한 사용자 잔액이 조회됨
        assertThat(foundBalance)
            .as("등록된 사용자의 잔액이 조회되어야 함")
            .isPresent();
        
        assertThat(foundBalance.get().getAmount())
            .as("저장된 잔액과 조회된 잔액이 일치해야 함")
            .isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("잔액 충전 후 정확한 금액이 반영된다")
    void reflectsAccurateAmountAfterCharging() {
        // Given - 기존 잔액이 있는 사용자
        // Why: 잔액 충전 시 기존 잔액과의 정확한 합산이 중요한 비즈니스 로직
        Long userId = 1L;
        BigDecimal initialAmount = BigDecimal.valueOf(10000);
        BigDecimal chargeAmount = BigDecimal.valueOf(20000);
        
        Balance initialBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(userId)
            .amount(initialAmount)
            .build();
        balanceRepository.save(initialBalance);

        // When - 잔액 충전
        Balance balance = balanceRepository.findByUserId(userId).get();
        balance.addAmount(chargeAmount);
        Balance updatedBalance = balanceRepository.save(balance);

        // Then - 충전 금액이 정확히 반영됨
        BalanceAssertions.assertCharged(updatedBalance, initialAmount, chargeAmount);
        
        // 다시 조회해도 동일한 잔액 유지
        Balance reloadedBalance = balanceRepository.findByUserId(userId).get();
        assertThat(reloadedBalance.getAmount())
            .as("충전 후 재조회 시에도 동일한 잔액을 유지해야 함")
            .isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    @DisplayName("결제 시 잔액이 정확히 차감된다")
    void deductsAccurateAmountDuringPayment() {
        // Given - 충분한 잔액이 있는 사용자
        // Why: 결제 시 잔액 차감의 정확성이 핵심 비즈니스 로직
        Long userId = 1L;
        BigDecimal initialAmount = BigDecimal.valueOf(50000);
        BigDecimal paymentAmount = BigDecimal.valueOf(15000);
        
        Balance initialBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(userId)
            .amount(initialAmount)
            .build();
        balanceRepository.save(initialBalance);

        // When - 결제로 인한 잔액 차감
        Balance balance = balanceRepository.findByUserId(userId).get();
        balance.subtractAmount(paymentAmount);
        Balance updatedBalance = balanceRepository.save(balance);

        // Then - 결제 금액이 정확히 차감됨
        BalanceAssertions.assertDeducted(updatedBalance, initialAmount, paymentAmount);
        
        assertThat(updatedBalance.getAmount())
            .as("결제 후 잔액은 초기잔액 - 결제금액이어야 함")
            .isEqualByComparingTo(BigDecimal.valueOf(35000));
    }

    @Test
    @DisplayName("동시 충전 요청 시 모든 금액이 정확히 반영된다")
    void reflectsAllAmountsAccuratelyDuringSimultaneousCharging() {
        // Given - 여러 사용자가 동시에 잔액 충전하는 상황
        // Why: 동시성 환경에서 잔액 정합성 보장이 중요한 비즈니스 요구사항
        Long baseUserId = 100L;
        int simultaneousUsers = 10;
        BigDecimal chargeAmount = BigDecimal.valueOf(10000);

        // 각 사용자의 초기 잔액 생성
        for (int i = 0; i < simultaneousUsers; i++) {
            Balance userBalance = TestBuilder.BalanceBuilder
                .defaultBalance()
                .userId(baseUserId + i)
                .amount(BigDecimal.ZERO)
                .build();
            balanceRepository.save(userBalance);
        }

        // When - 10명이 동시에 1만원씩 충전
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(simultaneousUsers, () -> {
                Long userId = baseUserId + Thread.currentThread().getId() % simultaneousUsers;
                Balance balance = balanceRepository.findByUserId(userId).orElse(null);
                if (balance != null) {
                    balance.addAmount(chargeAmount);
                    balanceRepository.save(balance);
                    return true;
                }
                return false;
            });

        // Then - 모든 충전이 성공하고 정확한 잔액 유지
        assertThat(result.getSuccessCount())
            .as("동시 충전에서 모든 요청이 성공해야 함")
            .isEqualTo(simultaneousUsers);

        // 각 사용자 잔액 검증
        for (int i = 0; i < simultaneousUsers; i++) {
            Balance finalBalance = balanceRepository.findByUserId(baseUserId + i).get();
            assertThat(finalBalance.getAmount())
                .as("각 사용자별로 정확한 충전 금액이 반영되어야 함")
                .isEqualByComparingTo(chargeAmount);
        }
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 빈 결과를 반환한다")
    void returnsEmptyForNonExistentUser() {
        // Given - 등록되지 않은 사용자 ID
        // Why: 잘못된 사용자 ID로 조회 시 안전한 처리 확인
        Long nonExistentUserId = 999L;

        // When - 존재하지 않는 사용자 잔액 조회
        Optional<Balance> result = balanceRepository.findByUserId(nonExistentUserId);

        // Then - 빈 결과 반환
        assertThat(result)
            .as("존재하지 않는 사용자에 대해서는 빈 결과를 반환해야 함")
            .isEmpty();
    }

    @Test
    @DisplayName("동일한 사용자의 중복 잔액 생성을 방지한다")
    void preventsMultipleBalancesForSameUser() {
        // Given - 이미 잔액이 있는 사용자
        // Why: 한 사용자당 하나의 잔액만 유지하는 비즈니스 규칙 검증
        Long userId = 1L;
        Balance firstBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(userId)
            .amount(BigDecimal.valueOf(10000))
            .build();
        balanceRepository.save(firstBalance);

        // When - 동일 사용자로 새로운 잔액 생성 시도
        Balance secondBalance = TestBuilder.BalanceBuilder
            .defaultBalance()
            .userId(userId)
            .amount(BigDecimal.valueOf(20000))
            .build();
        balanceRepository.save(secondBalance);

        // Then - 기존 잔액이 업데이트됨 (중복 생성되지 않음)
        Optional<Balance> result = balanceRepository.findByUserId(userId);
        assertThat(result).as("사용자별 잔액은 하나만 존재해야 함").isPresent();
        
        // 최신 저장된 금액으로 업데이트됨
        assertThat(result.get().getAmount())
            .as("동일 사용자 재저장 시 최신 금액으로 업데이트되어야 함")
            .isEqualByComparingTo(BigDecimal.valueOf(20000));
    }
}