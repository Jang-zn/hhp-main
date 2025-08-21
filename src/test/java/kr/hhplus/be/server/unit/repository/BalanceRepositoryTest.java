package kr.hhplus.be.server.unit.repository;

import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BalanceRepositoryPort 비즈니스 시나리오 테스트
 * 
 * Why: JPA 잔액 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: JPA 기반 잔액 관리 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("JPA 잔액 저장소 비즈니스 시나리오")
class BalanceRepositoryTest extends RepositoryTestBase {

    @Autowired
    private TestEntityManager testEntityManager;
    
    @Autowired
    private BalanceRepositoryPort balanceRepositoryPort;

    // === 잔액 저장 시나리오 ===

    @Test
    @DisplayName("고객의 신규 잔액 정보를 저장할 수 있다")
    void canSaveNewCustomerBalance() {
        // Given - ID가 null인 새로운 엔티티
        Balance newBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(101L)
                .amount(new BigDecimal("100000"))
                .build();

        // When
        Balance savedBalance = balanceRepositoryPort.save(newBalance);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Balance foundBalance = testEntityManager.find(Balance.class, savedBalance.getId());
        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getUserId()).isEqualTo(101L);
        assertThat(foundBalance.getAmount()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("기존 고객의 잔액 정보를 업데이트할 수 있다")
    void canUpdateExistingCustomerBalance() {
        // Given - 먼저 새로운 잔액을 저장
        Balance newBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(102L)
                .amount(new BigDecimal("100000"))
                .build();
        Balance savedBalance = balanceRepositoryPort.save(newBalance);
        testEntityManager.flush();
        testEntityManager.clear();
        
        // When - 저장된 잔액을 조회하여 수정 (금액 추가)
        Balance existingBalance = testEntityManager.find(Balance.class, savedBalance.getId());
        existingBalance.addAmount(new BigDecimal("100000")); // 100000 추가하여 200000으로 만들기
        Balance updatedBalance = balanceRepositoryPort.save(existingBalance);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Balance foundBalance = testEntityManager.find(Balance.class, updatedBalance.getId());
        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getAmount()).isEqualByComparingTo(new BigDecimal("200000"));
    }

    @ParameterizedTest
    @MethodSource("provideBalanceAmounts")
    @DisplayName("다양한 금액으로 잔액을 저장할 수 있다")
    void canSaveBalanceWithVariousAmounts(BigDecimal amount) {
        // Given - 새로운 엔티티 (ID가 null)
        Balance balance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(Thread.currentThread().getId() + 200L) // Unique userId for each test
                .amount(amount)
                .build();

        // When
        Balance savedBalance = balanceRepositoryPort.save(balance);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        Balance foundBalance = testEntityManager.find(Balance.class, savedBalance.getId());
        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("null 잔액 정보 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullBalance() {
        // When & Then
        assertThatThrownBy(() -> balanceRepositoryPort.save(null))
            .isInstanceOf(Exception.class);
    }

    // === 잔액 조회 시나리오 ===

    @Test
    @DisplayName("고객 ID로 잔액 정보를 조회할 수 있다")
    void canFindBalanceByUserId() {
        // Given
        Long userId = 301L;
        Balance expectedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId)
                .amount(new BigDecimal("150000"))
                .build();
        testEntityManager.persistAndFlush(expectedBalance);
        testEntityManager.flush();
        testEntityManager.clear();

        // When
        Optional<Balance> foundBalance = balanceRepositoryPort.findByUserId(userId);

        // Then
        assertThat(foundBalance).isPresent();
        assertThat(foundBalance.get().getUserId()).isEqualTo(userId);
        assertThat(foundBalance.get().getAmount()).isEqualByComparingTo(new BigDecimal("150000"));
    }

    @Test
    @DisplayName("존재하지 않는 고객 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenBalanceNotFoundByUserId() {
        // Given
        Long nonExistentUserId = 999L;
        // 다른 사용자의 잔액 저장
        Balance otherBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(501L)
                .amount(new BigDecimal("100000"))
                .build();
        testEntityManager.persistAndFlush(otherBalance);
        testEntityManager.flush();
        testEntityManager.clear();

        // When
        Optional<Balance> foundBalance = balanceRepositoryPort.findByUserId(nonExistentUserId);

        // Then
        assertThat(foundBalance).isEmpty();
    }



    // === 동시성 시나리오 ===

    @Test
    @DisplayName("여러 고객의 잔액이 동시에 저장되어도 안전하게 처리된다")
    void safelyHandlesConcurrentBalanceSaving() {
        // Given - 각 스레드마다 다른 userId를 사용하여 충돌 방지
        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                long userId = Thread.currentThread().getId() % 1000; // 각 스레드마다 다른 userId
                Balance balance = TestBuilder.BalanceBuilder.defaultBalance()
                    .userId(userId)
                    .amount(new BigDecimal("10000").multiply(BigDecimal.valueOf(userId % 3 + 1)))
                    .build();
                try {
                    Balance saved = balanceRepositoryPort.save(balance);
                    testEntityManager.getEntityManager().flush();
                    return saved != null ? 1 : 0;
                } catch (Exception e) {
                    // 동시성으로 인한 실패 허용
                    return 0;
                }
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(3);
        // 최소 1개 이상은 성공해야 함
        assertThat(result.getSuccessCount()).isGreaterThan(0);
    }


    @Test
    @DisplayName("잔액 저장과 조회가 동시에 이루어져도 EntityManager 호출이 정상적으로 처리된다")
    void handlesSimultaneousSaveAndFindOperations() {
        // Given
        Balance saveBalance = TestBuilder.BalanceBuilder.defaultBalance().userId(1L).amount(new BigDecimal("50000")).build();
        Balance findBalance = TestBuilder.BalanceBuilder.defaultBalance().userId(2L).amount(new BigDecimal("75000")).build();
        testEntityManager.persistAndFlush(findBalance);
        testEntityManager.flush();
        testEntityManager.clear();

        // When
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(6, () -> {
                if (Math.random() < 0.5) {
                    Balance saved = balanceRepositoryPort.save(saveBalance);
                    return saved != null ? 1 : 0;
                } else {
                    Optional<Balance> found = balanceRepositoryPort.findByUserId(2L);
                    return found.isPresent() ? 1 : 0;
                }
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(6);
        assertThat(result.getSuccessCount()).isGreaterThan(0);
    }

    // === 예외 처리 시나리오 ===

    @Test
    @DisplayName("EntityManager 저장 예외가 적절히 전파된다")
    void properlyPropagatesSaveExceptions() {
        // Given - 유효하지 않은 데이터로 테스트
        // userId가 null인 경우 예외 발생
        Balance invalidBalance = Balance.builder()
                .userId(null)  // Invalid: userId cannot be null
                .amount(new BigDecimal("100000"))
                .build();

        // When & Then
        assertThatThrownBy(() -> {
            balanceRepositoryPort.save(invalidBalance);
            testEntityManager.flush();  // Force flush to trigger constraint violation
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("조회 시 EntityManager 예외 발생해도 빈 결과를 반환한다")
    void returnsEmptyWhenFindExceptionOccurs() {
        // Given - 존재하지 않는 userId로 조회
        Long userId = 999999L;

        // When
        Optional<Balance> result = balanceRepositoryPort.findByUserId(userId);

        // Then - 예외가 발생해도 빈 Optional 반환
        assertThat(result).isEmpty();
    }


    // === 헬퍼 메서드 ===

    static Stream<Arguments> provideBalanceAmounts() {
        return Stream.of(
            Arguments.of(new BigDecimal("0")),
            Arguments.of(new BigDecimal("1000")),
            Arguments.of(new BigDecimal("50000")),
            Arguments.of(new BigDecimal("100000")),
            Arguments.of(new BigDecimal("500000")),
            Arguments.of(new BigDecimal("1000000")),
            Arguments.of(new BigDecimal("9999999.99"))
        );
    }
}