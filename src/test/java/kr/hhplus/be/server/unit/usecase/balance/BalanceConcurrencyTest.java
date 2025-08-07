package kr.hhplus.be.server.unit.usecase.balance;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import kr.hhplus.be.server.domain.usecase.balance.DeductBalanceUseCase;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("잔액 동시성 단위 테스트")
class BalanceConcurrencyTest {

    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;

    private ChargeBalanceUseCase chargeBalanceUseCase;
    private DeductBalanceUseCase deductBalanceUseCase;
    private Balance testBalance;

    @BeforeEach
    void setUp() {
        chargeBalanceUseCase = new ChargeBalanceUseCase(balanceRepositoryPort);
        deductBalanceUseCase = new DeductBalanceUseCase(balanceRepositoryPort);
        
        testBalance = Balance.builder()
                .id(1L)
                .userId(1L)
                .amount(new BigDecimal("100000"))
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("동시 충전 시 낙관적 락 충돌 확인")
    void testConcurrentCharge_OptimisticLockConflict() {
        // Given: 초기 잔액 10만원
        AtomicReference<Balance> balanceRef = new AtomicReference<>(testBalance);
        
        when(balanceRepositoryPort.findByUserId(1L))
                .thenAnswer(invocation -> Optional.of(balanceRef.get()));
        
        // OptimisticLockingFailureException을 시뮬레이션
        when(balanceRepositoryPort.save(any(Balance.class)))
                .thenAnswer(invocation -> {
                    Balance balance = invocation.getArgument(0);
                    
                    // 50% 확률로 낙관적 락 충돌 발생
                    if (Math.random() < 0.5) {
                        throw new OptimisticLockingFailureException("낙관적 락 충돌");
                    }
                    
                    // 버전은 JPA가 자동 관리하므로 테스트에서만 수동 설정
                    balanceRef.set(balance);
                    return balance;
                });

        // When: 10개 스레드가 동시에 1만원씩 충전 시도
        int threadCount = 10;
        BigDecimal chargeAmount = new BigDecimal("10000");

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> chargeBalanceUseCase.execute(1L, chargeAmount)
        );

        // Then: 낙관적 락 충돌로 인해 일부 요청이 실패할 수 있음
        System.out.printf("동시 충전 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("실행 시간: %dms%n", result.getExecutionTimeMs());

        // 실패한 요청들의 에러 메시지 확인
        result.getErrorMessages().forEach(System.out::println);

        if (result.getSuccessCount() > 0) {
            Balance finalBalance = balanceRef.get();
            System.out.printf("최종 잔액: %s%n", finalBalance.getAmount());
            
            // 성공한 만큼 잔액이 증가했는지 확인
            BigDecimal expectedAmount = testBalance.getAmount()
                    .add(chargeAmount.multiply(new BigDecimal(result.getSuccessCount())));
            assertThat(finalBalance.getAmount()).isEqualTo(expectedAmount);
        }
    }

    @Test
    @DisplayName("동시 차감 시 잔액 부족 처리")
    void testConcurrentDeduct_InsufficientBalance() {
        // Given: 잔액 5만원
        Balance limitedBalance = Balance.builder()
                .id(1L)
                .userId(1L)
                .amount(new BigDecimal("50000"))
                .version(0L)
                .build();

        AtomicReference<Balance> balanceRef = new AtomicReference<>(limitedBalance);
        when(balanceRepositoryPort.findByUserId(1L))
                .thenAnswer(invocation -> Optional.of(balanceRef.get()));
        when(balanceRepositoryPort.save(any(Balance.class)))
                .thenAnswer(invocation -> {
                    Balance balance = invocation.getArgument(0);
                    // 버전은 JPA가 자동 관리하므로 테스트에서만 수동 설정
                    balanceRef.set(balance);
                    return balance;
                });

        // When: 10개 스레드가 동시에 1만원씩 차감 시도 (총 10만원, 잔액 부족)
        int threadCount = 10;
        BigDecimal deductAmount = new BigDecimal("10000");

        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeInParallel(
                threadCount,
                () -> deductBalanceUseCase.execute(1L, deductAmount)
        );

        // Then: 5개만 성공하고 나머지는 실패해야 함
        Balance finalBalance = balanceRef.get();
        System.out.printf("잔액 부족 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("최종 잔액: %s%n", finalBalance.getAmount());

        // 성공한 차감 횟수 확인 (최대 5번)
        assertThat(result.getSuccessCount()).isLessThanOrEqualTo(5);
        
        // 잔액 부족으로 인한 실패 발생 확인
        assertThat(result.getFailureCount()).isGreaterThan(0);
        
        // 최종 잔액은 0 이상이어야 함
        assertThat(finalBalance.getAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("동시 충전과 차감 혼합 테스트")
    void testConcurrentChargeAndDeduct() {
        // Given: 초기 잔액 5만원
        Balance initialBalance = Balance.builder()
                .id(1L)
                .userId(1L)
                .amount(new BigDecimal("50000"))
                .version(0L)
                .build();

        AtomicReference<Balance> balanceRef = new AtomicReference<>(initialBalance);
        when(balanceRepositoryPort.findByUserId(1L))
                .thenAnswer(invocation -> Optional.of(balanceRef.get()));
        when(balanceRepositoryPort.save(any(Balance.class)))
                .thenAnswer(invocation -> {
                    Balance balance = invocation.getArgument(0);
                    // 버전은 JPA가 자동 관리하므로 테스트에서만 수동 설정
                    balanceRef.set(balance);
                    return balance;
                });

        // When: 충전 3개 스레드(각 2만원), 차감 5개 스레드(각 1만원) 동시 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeMultipleTasks(
                java.util.List.of(
                        // 충전 작업들 (3개 * 20,000 = 60,000)
                        () -> chargeBalanceUseCase.execute(1L, new BigDecimal("20000")),
                        () -> chargeBalanceUseCase.execute(1L, new BigDecimal("20000")),
                        () -> chargeBalanceUseCase.execute(1L, new BigDecimal("20000")),
                        // 차감 작업들 (5개 * 10,000 = 50,000)
                        () -> deductBalanceUseCase.execute(1L, new BigDecimal("10000")),
                        () -> deductBalanceUseCase.execute(1L, new BigDecimal("10000")),
                        () -> deductBalanceUseCase.execute(1L, new BigDecimal("10000")),
                        () -> deductBalanceUseCase.execute(1L, new BigDecimal("10000")),
                        () -> deductBalanceUseCase.execute(1L, new BigDecimal("10000"))
                )
        );

        // Then: 결과 분석
        Balance finalBalance = balanceRef.get();
        System.out.printf("충전/차감 혼합 테스트 결과: 성공 %d, 실패 %d%n", 
                result.getSuccessCount(), result.getFailureCount());
        System.out.printf("최종 잔액: %s%n", finalBalance.getAmount());

        // 최종 잔액은 0 이상이어야 함
        assertThat(finalBalance.getAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        
        // 모든 작업이 성공했다면 최종 잔액 계산 가능
        if (result.getSuccessCount() == 8) {
            // 초기 50,000 + 충전 60,000 - 차감 50,000 = 60,000
            assertThat(finalBalance.getAmount()).isEqualTo(new BigDecimal("60000"));
        }
    }

    @Test
    @DisplayName("낙관적 락 재시도 메커니즘 테스트")
    void testOptimisticLockRetryMechanism() {
        // Given: 초기 잔액과 재시도 설정
        AtomicReference<Balance> balanceRef = new AtomicReference<>(testBalance);
        AtomicReference<Integer> attemptCount = new AtomicReference<>(0);
        
        when(balanceRepositoryPort.findByUserId(1L))
                .thenAnswer(invocation -> Optional.of(balanceRef.get()));
        
        when(balanceRepositoryPort.save(any(Balance.class)))
                .thenAnswer(invocation -> {
                    Balance balance = invocation.getArgument(0);
                    int attempts = attemptCount.updateAndGet(count -> count + 1);
                    
                    // 처음 2번은 실패, 3번째부터 성공하도록 설정
                    if (attempts <= 2) {
                        throw new OptimisticLockingFailureException("낙관적 락 충돌 - 재시도 필요");
                    }
                    
                    // 버전은 JPA가 자동 관리하므로 테스트에서만 수동 설정
                    balanceRef.set(balance);
                    return balance;
                });

        // When: 충전 실행 (재시도 메커니즘으로 최종 성공해야 함)
        assertThatNoException().isThrownBy(() -> {
            Balance result = chargeBalanceUseCase.execute(1L, new BigDecimal("5000"));
            System.out.printf("재시도 성공 후 잔액: %s%n", result.getAmount());
        });

        // Then: 재시도로 인해 여러 번 시도되었는지 확인
        System.out.printf("총 시도 횟수: %d%n", attemptCount.get());
        assertThat(attemptCount.get()).isGreaterThan(2);
        
        Balance finalBalance = balanceRef.get();
        assertThat(finalBalance.getAmount()).isEqualTo(new BigDecimal("105000"));
    }

    @Test
    @DisplayName("대량 동시 잔액 조작 부하 테스트")
    void testHighConcurrencyBalanceOperations() {
        // Given: 충분한 초기 잔액
        Balance largeBalance = Balance.builder()
                .id(1L)
                .userId(1L)
                .amount(new BigDecimal("1000000")) // 100만원
                .version(0L)
                .build();

        AtomicReference<Balance> balanceRef = new AtomicReference<>(largeBalance);
        when(balanceRepositoryPort.findByUserId(1L))
                .thenAnswer(invocation -> Optional.of(balanceRef.get()));
        when(balanceRepositoryPort.save(any(Balance.class)))
                .thenAnswer(invocation -> {
                    Balance balance = invocation.getArgument(0);
                    
                    // 10% 확률로 낙관적 락 충돌
                    if (Math.random() < 0.1) {
                        throw new OptimisticLockingFailureException("낙관적 락 충돌");
                    }
                    
                    // 버전은 JPA가 자동 관리하므로 테스트에서만 수동 설정
                    balanceRef.set(balance);
                    return balance;
                });

        // When: 100회 실행, 15개 동시 스레드로 1000원씩 차감
        ConcurrencyTestHelper.ConcurrencyTestResult result = ConcurrencyTestHelper.executeLoadTest(
                () -> {
                    deductBalanceUseCase.execute(1L, new BigDecimal("1000"));
                    return null;
                },
                100, // 총 실행 횟수
                15   // 동시 스레드 수
        );

        // Then: 성능 및 정확성 검증
        Balance finalBalance = balanceRef.get();
        System.out.printf("부하 테스트 결과: 성공 %d, 실패 %d, 실행시간 %dms%n", 
                result.getSuccessCount(), result.getFailureCount(), result.getExecutionTimeMs());
        System.out.printf("최종 잔액: %s%n", finalBalance.getAmount());

        // 성공률이 높아야 함 (잔액이 충분하므로)
        assertThat(result.getSuccessRate()).isGreaterThan(85.0);
        
        // 성공한 만큼 잔액이 차감되었는지 확인
        BigDecimal expectedAmount = largeBalance.getAmount()
                .subtract(new BigDecimal("1000").multiply(new BigDecimal(result.getSuccessCount())));
        assertThat(finalBalance.getAmount()).isEqualTo(expectedAmount);
        
        // 적절한 성능 기준
        assertThat(result.getExecutionTimeMs()).isLessThan(1500);
    }
}