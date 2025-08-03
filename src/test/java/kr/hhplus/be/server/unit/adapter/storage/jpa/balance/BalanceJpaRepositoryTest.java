package kr.hhplus.be.server.unit.adapter.storage.jpa.balance;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.BalanceJpaRepository;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("BalanceJpaRepository 단위 테스트")
class BalanceJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Balance> typedQuery;

    private BalanceJpaRepository balanceJpaRepository;

    @BeforeEach
    void setUp() {
        balanceJpaRepository = new BalanceJpaRepository(entityManager);
    }

    @Nested
    @DisplayName("잔액 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 잔액 저장 (persist)")
        void save_NewBalance_Success() {
            // given
            Balance balance = Balance.builder()
                    .userId(1L)
                    .amount(new BigDecimal("100000"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(balance);

            // when
            Balance savedBalance = balanceJpaRepository.save(balance);

            // then
            assertThat(savedBalance).isEqualTo(balance);
            verify(entityManager, times(1)).persist(balance);
            verify(entityManager, never()).merge(any());
        }

        @Test
        @DisplayName("성공케이스: 기존 잔액 업데이트 (merge)")
        void save_ExistingBalance_Success() {
            // given
            Balance balance = Balance.builder()
                    .id(1L)
                    .userId(1L)
                    .amount(new BigDecimal("150000"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(entityManager.merge(balance)).thenReturn(balance);

            // when
            Balance savedBalance = balanceJpaRepository.save(balance);

            // then
            assertThat(savedBalance).isEqualTo(balance);
            verify(entityManager, times(1)).merge(balance);
            verify(entityManager, never()).persist(any());
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.jpa.balance.BalanceJpaRepositoryTest#provideBalanceData")
        @DisplayName("성공케이스: 다양한 잔액 데이터로 저장")
        void save_WithDifferentBalanceData(String userName, String amount) {
            // given
            Balance balance = Balance.builder()
                    .userId(2L)
                    .amount(new BigDecimal(amount))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(balance);

            // when
            Balance savedBalance = balanceJpaRepository.save(balance);

            // then
            assertThat(savedBalance).isNotNull();
            assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal(amount));
            assertThat(savedBalance.getUserId()).isEqualTo(2L);
            verify(entityManager, times(1)).persist(balance);
        }

        @Test
        @DisplayName("실패케이스: EntityManager persist 예외")
        void save_PersistException() {
            // given
            Balance balance = Balance.builder()
                    .userId(1L)
                    .amount(new BigDecimal("100000"))
                    .build();

            doThrow(new RuntimeException("DB 연결 실패")).when(entityManager).persist(balance);

            // when & then
            assertThatThrownBy(() -> balanceJpaRepository.save(balance))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 연결 실패");
        }

        @Test
        @DisplayName("실패케이스: EntityManager merge 예외")
        void save_MergeException() {
            // given
            Balance balance = Balance.builder()
                    .id(1L)
                    .userId(1L)
                    .amount(new BigDecimal("100000"))
                    .build();

            when(entityManager.merge(balance)).thenThrow(new RuntimeException("트랜잭션 오류"));

            // when & then
            assertThatThrownBy(() -> balanceJpaRepository.save(balance))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("트랜잭션 오류");
        }
    }

    @Nested
    @DisplayName("잔액 조회 테스트")
    class FindByUserTests {

        @Test
        @DisplayName("성공케이스: 사용자로 잔액 조회")
        void findByUser_Success() {
            // given
            Balance expectedBalance = Balance.builder()
                    .id(1L)
                    .userId(1L)
                    .amount(new BigDecimal("50000"))
                    .build();

            when(entityManager.createQuery(anyString(), eq(Balance.class))).thenReturn(typedQuery);
            when(typedQuery.setParameter("userId", 1L)).thenReturn(typedQuery);
            when(typedQuery.getSingleResult()).thenReturn(expectedBalance);

            // when
            Optional<Balance> foundBalance = balanceJpaRepository.findByUserId(1L);

            // then
            assertThat(foundBalance).isPresent();
            assertThat(foundBalance.get()).isEqualTo(expectedBalance);
            verify(entityManager).createQuery("SELECT b FROM Balance b WHERE b.userId = :userId", Balance.class);
            verify(typedQuery).setParameter("userId", 1L);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 잔액 조회")
        void findByUser_NotFound() {
            // given
            when(entityManager.createQuery(anyString(), eq(Balance.class))).thenReturn(typedQuery);
            when(typedQuery.setParameter("userId", 999L)).thenReturn(typedQuery);
            when(typedQuery.getSingleResult()).thenThrow(new NoResultException());

            // when
            Optional<Balance> foundBalance = balanceJpaRepository.findByUserId(999L);

            // then
            assertThat(foundBalance).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: JPA 쿼리 실행 중 예외 발생")
        void findByUser_QueryException() {
            // given
            when(entityManager.createQuery(anyString(), eq(Balance.class)))
                    .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

            // when
            Optional<Balance> foundBalance = balanceJpaRepository.findByUserId(1L);

            // then
            assertThat(foundBalance).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.jpa.balance.BalanceJpaRepositoryTest#provideInvalidUsers")
        @DisplayName("실패케이스: 다양한 유효하지 않은 사용자로 조회")
        void findByUser_WithInvalidUsers(User invalidUser) {
            // given
            when(entityManager.createQuery(anyString(), eq(Balance.class))).thenReturn(typedQuery);
            when(typedQuery.setParameter("userId", invalidUser.getId())).thenReturn(typedQuery);
            when(typedQuery.getSingleResult()).thenThrow(new NoResultException());

            // when
            Optional<Balance> foundBalance = balanceJpaRepository.findByUserId(invalidUser.getId());

            // then
            assertThat(foundBalance).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 및 성능 테스트")
    class ConcurrencyAndPerformanceTests {

        @Test
        @DisplayName("동시성 테스트: 동일 사용자 잔액 동시 저장")
        void save_ConcurrentSaveForSameUser() throws Exception {
            // given
            Long userId = 100L;

            int numberOfThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successCount = new AtomicInteger(0);

            // EntityManager mock 설정
            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(entityManager).persist(any(Balance.class));

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when
            for (int i = 0; i < numberOfThreads; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Balance balance = Balance.builder()
                                .userId(userId)
                                .amount(new BigDecimal("1000"))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        balanceJpaRepository.save(balance);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(numberOfThreads);
            verify(entityManager, times(numberOfThreads)).persist(any(Balance.class));

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 서로 다른 사용자 잔액 동시 조회")
        void findByUser_ConcurrentReadsForDifferentUsers() throws Exception {
            // given
            int numberOfUsers = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfUsers);
            AtomicInteger successfulReads = new AtomicInteger(0);

            // Mock 설정
            when(entityManager.createQuery(anyString(), eq(Balance.class))).thenReturn(typedQuery);
            when(typedQuery.setParameter(eq("userId"), any(Long.class))).thenReturn(typedQuery);
            when(typedQuery.getSingleResult()).thenAnswer(invocation -> {
                successfulReads.incrementAndGet();
                return Balance.builder()
                        .id(1L)
                        .userId(1L)
                        .amount(new BigDecimal("1000"))
                        .build();
            });

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when
            for (int i = 0; i < numberOfUsers; i++) {
                final int userId = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Optional<Balance> balance = balanceJpaRepository.findByUserId((long) userId);
                        assertThat(balance).isPresent();
                    } catch (Exception e) {
                        System.err.println("Error for user " + userId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successfulReads.get()).isEqualTo(numberOfUsers);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("성능 테스트: 대량 저장 작업")
        void save_BulkInsertPerformance() throws Exception {
            // given
            int numberOfOperations = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            AtomicInteger completedOperations = new AtomicInteger(0);

            doAnswer(invocation -> {
                completedOperations.incrementAndGet();
                return null;
            }).when(entityManager).persist(any(Balance.class));

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();

            // when
            for (int i = 0; i < numberOfOperations; i++) {
                final int operationId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    Balance balance = Balance.builder()
                            .userId((long) operationId)
                            .amount(new BigDecimal(String.valueOf(operationId * 1000)))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    
                    balanceJpaRepository.save(balance);
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            long endTime = System.currentTimeMillis();

            // then
            assertThat(completedOperations.get()).isEqualTo(numberOfOperations);
            long executionTime = endTime - startTime;
            System.out.println("대량 저장 작업 실행 시간: " + executionTime + "ms");
            assertThat(executionTime).isLessThan(5000); // 5초 이내 완료

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {


        @Test
        @DisplayName("실패케이스: 트랜잭션 롤백 시나리오")
        void save_TransactionRollback() {
            // given
            Balance balance = Balance.builder()
                    .userId(1L)
                    .amount(new BigDecimal("1000"))
                    .build();

            doThrow(new RuntimeException("트랜잭션 롤백")).when(entityManager).persist(balance);

            // when & then
            assertThatThrownBy(() -> balanceJpaRepository.save(balance))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("트랜잭션 롤백");
        }
    }

    private static Stream<Arguments> provideBalanceData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "50000"),
                Arguments.of("박민수", "0"),
                Arguments.of("최유리", "999999999")
        );
    }

    private static Stream<Arguments> provideInvalidUsers() {
        return Stream.of(
                Arguments.of(User.builder().id(0L).build()),
                Arguments.of(User.builder().id(-1L).build()),
                Arguments.of(User.builder().id(Long.MAX_VALUE).build()),
                Arguments.of(User.builder().id(Long.MIN_VALUE).build())
        );
    }
}