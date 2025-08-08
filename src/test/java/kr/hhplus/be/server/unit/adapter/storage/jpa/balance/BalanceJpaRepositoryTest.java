package kr.hhplus.be.server.unit.adapter.storage.jpa.balance;

import kr.hhplus.be.server.adapter.storage.jpa.BalanceJpaRepository;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BalanceJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: JPA 잔액 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: JPA 기반 잔액 관리 시나리오를 반영한 단위 테스트로 구성
 */
@DataJpaTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("JPA 잔액 저장소 비즈니스 시나리오")
class BalanceJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;
    
    @Mock
    private TypedQuery<Balance> balanceQuery;
    
    @Mock
    private TypedQuery<Long> countQuery;

    private BalanceJpaRepository balanceJpaRepository;

    @BeforeEach
    void setUp() {
        balanceJpaRepository = new BalanceJpaRepository(entityManager);
    }

    // === 잔액 저장 시나리오 ===

    @Test
    @DisplayName("고객의 신규 잔액 정보를 저장할 수 있다")
    void canSaveNewCustomerBalance() {
        // Given - ID가 null인 새로운 엔티티
        Balance newBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(1L)
                .amount(new BigDecimal("100000"))
                .build();

        // When
        balanceJpaRepository.save(newBalance);

        // Then - 새로운 엔티티이므로 persist 호출
        verify(entityManager).persist(newBalance);
    }

    @Test
    @DisplayName("기존 고객의 잔액 정보를 업데이트할 수 있다")
    void canUpdateExistingCustomerBalance() {
        // Given
        Balance existingBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .id(1L)
                .userId(1L)
                .amount(new BigDecimal("200000"))
                .build();

        // When
        balanceJpaRepository.save(existingBalance);

        // Then
        verify(entityManager).merge(existingBalance);
    }

    @ParameterizedTest
    @MethodSource("provideBalanceAmounts")
    @DisplayName("다양한 금액으로 잔액을 저장할 수 있다")
    void canSaveBalanceWithVariousAmounts(BigDecimal amount) {
        // Given - 새로운 엔티티 (ID가 null)
        Balance balance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(1L)
                .amount(amount)
                .build();

        // When
        balanceJpaRepository.save(balance);

        // Then - 새로운 엔티티이므로 persist 호출
        verify(entityManager).persist(balance);
    }

    @Test
    @DisplayName("null 잔액 정보 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullBalance() {
        // When & Then
        assertThatThrownBy(() -> balanceJpaRepository.save(null))
            .isInstanceOf(NullPointerException.class);
            
        verify(entityManager, never()).merge(any());
        verify(entityManager, never()).persist(any());
    }

    // === 잔액 조회 시나리오 ===

    @Test
    @DisplayName("고객 ID로 잔액 정보를 조회할 수 있다")
    void canFindBalanceByUserId() {
        // Given
        Long userId = 1L;
        Balance expectedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId)
                .amount(new BigDecimal("150000"))
                .build();
                
        when(entityManager.createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class))
            .thenReturn(balanceQuery);
        when(balanceQuery.setParameter("userId", userId)).thenReturn(balanceQuery);
        when(balanceQuery.getSingleResult()).thenReturn(expectedBalance);

        // When
        Optional<Balance> foundBalance = balanceJpaRepository.findByUserId(userId);

        // Then
        assertThat(foundBalance).isPresent();
        assertThat(foundBalance.get().getUserId()).isEqualTo(userId);
        assertThat(foundBalance.get().getAmount()).isEqualTo(new BigDecimal("150000"));
        
        verify(entityManager).createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class);
        verify(balanceQuery).setParameter("userId", userId);
        verify(balanceQuery).getSingleResult();
    }

    @Test
    @DisplayName("존재하지 않는 고객 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenBalanceNotFoundByUserId() {
        // Given
        Long nonExistentUserId = 999L;
        when(entityManager.createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class))
            .thenReturn(balanceQuery);
        when(balanceQuery.setParameter("userId", nonExistentUserId)).thenReturn(balanceQuery);
        when(balanceQuery.getSingleResult()).thenThrow(new NoResultException());

        // When
        Optional<Balance> foundBalance = balanceJpaRepository.findByUserId(nonExistentUserId);

        // Then
        assertThat(foundBalance).isEmpty();
        
        verify(entityManager).createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class);
        verify(balanceQuery).setParameter("userId", nonExistentUserId);
        verify(balanceQuery).getSingleResult();
    }



    // === 동시성 시나리오 ===

    @Test
    @DisplayName("여러 고객의 잔액이 동시에 저장되어도 안전하게 처리된다")
    void safelyHandlesConcurrentBalanceSaving() {
        // Given
        List<Balance> testBalances = List.of(
            TestBuilder.BalanceBuilder.defaultBalance().userId(1L).amount(new BigDecimal("10000")).build(),
            TestBuilder.BalanceBuilder.defaultBalance().userId(2L).amount(new BigDecimal("20000")).build(),
            TestBuilder.BalanceBuilder.defaultBalance().userId(3L).amount(new BigDecimal("30000")).build()
        );

        // When - EntityManager 호출 검증을 위한 동시 저장
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                Balance balance = testBalances.get((int)(Math.random() * testBalances.size()));
                balanceJpaRepository.save(balance);
                return 1;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(3);
        verify(entityManager, times(3)).persist(any(Balance.class));
    }

    @Test
    @DisplayName("동시 잔액 조회가 EntityManager를 통해 안전하게 처리된다")
    void safelyHandlesConcurrentBalanceQuerying() {
        // Given
        Long userId = 1L;
        Balance expectedBalance = TestBuilder.BalanceBuilder.defaultBalance()
                .userId(userId)
                .amount(new BigDecimal("100000"))
                .build();
                
        when(entityManager.createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class))
            .thenReturn(balanceQuery);
        when(balanceQuery.setParameter("userId", userId)).thenReturn(balanceQuery);
        when(balanceQuery.getSingleResult()).thenReturn(expectedBalance);

        // When - EntityManager 호출 검증을 위한 동시 조회
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(5, () -> {
                Optional<Balance> found = balanceJpaRepository.findByUserId(userId);
                return found.isPresent() ? 1 : 0;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getSuccessCount()).isEqualTo(5);
        verify(entityManager, times(5)).createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class);
        verify(balanceQuery, times(5)).setParameter("userId", userId);
        verify(balanceQuery, times(5)).getSingleResult();
    }

    @Test
    @DisplayName("잔액 저장과 조회가 동시에 이루어져도 EntityManager 호출이 정상적으로 처리된다")
    void handlesSimultaneousSaveAndFindOperations() {
        // Given
        Balance saveBalance = TestBuilder.BalanceBuilder.defaultBalance().userId(1L).amount(new BigDecimal("50000")).build();
        Balance findBalance = TestBuilder.BalanceBuilder.defaultBalance().userId(2L).amount(new BigDecimal("75000")).build();
        
        when(entityManager.createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class))
            .thenReturn(balanceQuery);
        when(balanceQuery.setParameter("userId", 2L)).thenReturn(balanceQuery);
        when(balanceQuery.getSingleResult()).thenReturn(findBalance);

        // When - 저장과 조회가 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(6, () -> {
                if (Math.random() < 0.5) {
                    balanceJpaRepository.save(saveBalance);
                    return 1;
                } else {
                    Optional<Balance> found = balanceJpaRepository.findByUserId(2L);
                    return found.isPresent() ? 1 : 0;
                }
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(6);
        verify(entityManager, atLeastOnce()).persist(any(Balance.class));
        verify(entityManager, atLeastOnce()).createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class);
    }

    // === 예외 처리 시나리오 ===

    @Test
    @DisplayName("EntityManager 저장 예외가 적절히 전파된다")
    void properlyPropagatesSaveExceptions() {
        // Given - ID가 null인 새로운 엔티티
        Balance balance = TestBuilder.BalanceBuilder.defaultBalance().build();
        RuntimeException expectedException = new RuntimeException("저장 실패");
        doThrow(expectedException).when(entityManager).persist(balance);

        // When & Then
        assertThatThrownBy(() -> balanceJpaRepository.save(balance))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("저장 실패");
            
        verify(entityManager).persist(balance);
    }

    @Test
    @DisplayName("조회 시 EntityManager 예외 발생해도 빈 결과를 반환한다")
    void returnsEmptyWhenFindExceptionOccurs() {
        // Given
        Long userId = 1L;
        RuntimeException expectedException = new RuntimeException("조회 실패");
        
        when(entityManager.createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class))
            .thenReturn(balanceQuery);
        when(balanceQuery.setParameter("userId", userId)).thenReturn(balanceQuery);
        when(balanceQuery.getSingleResult()).thenThrow(expectedException);

        // When
        Optional<Balance> result = balanceJpaRepository.findByUserId(userId);

        // Then - 예외가 발생해도 빈 Optional 반환
        assertThat(result).isEmpty();
        verify(balanceQuery).getSingleResult();
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