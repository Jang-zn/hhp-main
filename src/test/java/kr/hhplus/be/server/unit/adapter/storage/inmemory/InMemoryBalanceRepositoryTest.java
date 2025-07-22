package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryBalanceRepository;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

@DisplayName("InMemoryBalanceRepository 단위 테스트")
class InMemoryBalanceRepositoryTest {

    private InMemoryBalanceRepository balanceRepository;

    @BeforeEach
    void setUp() {
        balanceRepository = new InMemoryBalanceRepository();
    }

    @Nested
    @DisplayName("잔액 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 잔액 저장")
        void save_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // when
        Balance savedBalance = balanceRepository.save(balance);

        // then
        assertThat(savedBalance).isNotNull();
        assertThat(savedBalance.getId()).isNotNull();
        assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(savedBalance.getUser().getId()).isEqualTo(1L);
    }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryBalanceRepositoryTest#provideBalanceData")
        @DisplayName("성공케이스: 다양한 잔액 데이터로 저장")
        void save_WithDifferentBalanceData(String userName, String amount) {
            // given
            User user = User.builder()
                    .id(2L)
                    .name(userName)
                    .build();
            Balance balance = Balance.builder()
                    .user(user)
                    .amount(new BigDecimal(amount))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // when
            Balance savedBalance = balanceRepository.save(balance);

            // then
            assertThat(savedBalance).isNotNull();
            assertThat(savedBalance.getId()).isNotNull();
            assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal(amount));
            assertThat(savedBalance.getUser().getName()).isEqualTo(userName);
        }

        @Test
        @DisplayName("성공케이스: 동일 사용자 잔액 업데이트")
        void save_UpdateExistingBalance() {
            // given
            User user = User.builder()
                    .id(3L)
                    .name("테스트 사용자")
                    .build();
            Balance originalBalance = Balance.builder()
                    .user(user)
                    .amount(new BigDecimal("50000"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            balanceRepository.save(originalBalance);

            Balance updatedBalance = Balance.builder()
                    .user(user)
                    .amount(new BigDecimal("100000"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // when
            Balance savedBalance = balanceRepository.save(updatedBalance);

            // then
            assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal("100000"));
            Optional<Balance> foundBalance = balanceRepository.findByUser(user);
            assertThat(foundBalance).isPresent();
            assertThat(foundBalance.get().getAmount()).isEqualTo(new BigDecimal("100000"));
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryBalanceRepositoryTest#provideEdgeCaseAmounts")
        @DisplayName("성공케이스: 극한값 잔액으로 저장")
        void save_WithEdgeCaseAmounts(String description, String amount) {
            // given
            User user = User.builder()
                    .id(4L)
                    .name("엣지케이스 사용자")
                    .build();
            Balance balance = Balance.builder()
                    .user(user)
                    .amount(new BigDecimal(amount))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // when
            Balance savedBalance = balanceRepository.save(balance);

            // then
            assertThat(savedBalance).isNotNull();
            assertThat(savedBalance.getId()).isNotNull();
            assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal(amount));
        }

        @Test
        @DisplayName("실패케이스: null 잔액 객체 저장")
        void save_WithNullBalance() {
            // when & then
            assertThatThrownBy(() -> balanceRepository.save(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Balance cannot be null");
        }

        @Test
        @DisplayName("실패케이스: null 사용자가 포함된 잔액 저장")
        void save_WithNullUser() {
            // given
            Balance balance = Balance.builder()
                    .user(null)
                    .amount(new BigDecimal("100000"))
                    .build();

            // when & then
            assertThatThrownBy(() -> balanceRepository.save(balance))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Balance user cannot be null");
        }
    }

    @Nested
    @DisplayName("잔액 조회 테스트")
    class FindByUserTests {
        
        @Test
        @DisplayName("성공케이스: 사용자 ID로 잔액 조회")
        void findByUser_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .build();
        Balance balance = Balance.builder()
                .id(1L)
                .user(user)
                .amount(new BigDecimal("50000"))
                .build();
        balanceRepository.save(balance);

        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

        // then
        assertThat(foundBalance).isPresent();
        assertThat(foundBalance.get().getAmount()).isEqualTo(new BigDecimal("50000"));
    }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 잔액 조회")
        void findByUser_NotFound() {
        // given
        User user = User.builder().id(999L).build();

        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

            // then
            assertThat(foundBalance).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null 사용자 객체로 조회")
        void findByUser_WithNullUser() {
            // when & then
            assertThatThrownBy(() -> balanceRepository.findByUser(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User cannot be null");
        }

        @Test
        @DisplayName("실패케이스: null 사용자 ID로 조회")
        void findByUser_WithNullUserId() {
            // given
            User user = User.builder().id(null).name("테스트 사용자").build();
            
            // when & then
            assertThatThrownBy(() -> balanceRepository.findByUser(user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("실패케이스: 음수 사용자 ID로 조회")
        void findByUserId_WithNegativeUserId() {
        // given
        User user = User.builder().id(-1L).name("테스트 사용자").build();
        
        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

            // then
            assertThat(foundBalance).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryBalanceRepositoryTest#provideInvalidUserIds")
        @DisplayName("실패케이스: 유효하지 않은 사용자 ID들로 조회")
        void findByUserId_WithInvalidUserIds(Long invalidUserId) {
        // given
        User user = User.builder().id(invalidUserId).name("테스트 사용자").build();
        
        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

            // then
            assertThat(foundBalance).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 동일 사용자 잔액 동시 업데이트")
        void save_ConcurrentUpdatesForSameUser() throws Exception {
            // given
            User user = User.builder()
                    .id(100L)
                    .name("동시성 테스트 사용자")
                    .build();

            int numberOfThreads = 10;
            int updatesPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when - 동시에 잔액 업데이트
            for (int i = 0; i < numberOfThreads; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기
                        
                        for (int j = 0; j < updatesPerThread; j++) {
                            Balance balance = Balance.builder()
                                    .user(user)
                                    .amount(new BigDecimal("1000"))
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            balanceRepository.save(balance);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown(); // 모든 스레드 시작
            doneLatch.await(); // 모든 스레드 완료 대기

            // then - 최종 상태 검증
            Optional<Balance> finalBalance = balanceRepository.findByUser(user);
            assertThat(finalBalance).isPresent();
            assertThat(finalBalance.get().getAmount()).isEqualTo(new BigDecimal("1000"));
            assertThat(finalBalance.get().getUser().getId()).isEqualTo(100L);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 서로 다른 사용자 잔액 동시 생성")
        void save_ConcurrentCreationForDifferentUsers() throws Exception {
            // given
            int numberOfUsers = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfUsers);
            AtomicInteger successCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // when - 서로 다른 사용자들의 잔액을 동시에 생성
            for (int i = 0; i < numberOfUsers; i++) {
                final int userId = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        User user = User.builder()
                                .id((long) userId)
                                .name("사용자" + userId)
                                .build();
                        
                        Balance balance = Balance.builder()
                                .user(user)
                                .amount(new BigDecimal(String.valueOf(userId * 1000)))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        balanceRepository.save(balance);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 예외 발생시 로그 출력 (실제로는 로거 사용)
                        System.err.println("Error for user " + userId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then - 모든 사용자가 성공적으로 생성되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfUsers);
            
            // 각 사용자의 잔액이 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfUsers; i++) {
                User user = User.builder().id((long) i).name("사용자" + i).build();
                Optional<Balance> balance = balanceRepository.findByUser(user);
                assertThat(balance).isPresent();
                assertThat(balance.get().getAmount()).isEqualTo(new BigDecimal(String.valueOf(i * 1000)));
            }

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 업데이트")
        void concurrentReadAndWrite() throws Exception {
            // given
            User user = User.builder()
                    .id(200L)
                    .name("읽기쓰기 테스트 사용자")
                    .build();

            // 초기 잔액 설정
            Balance initialBalance = Balance.builder()
                    .user(user)
                    .amount(new BigDecimal("50000"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            balanceRepository.save(initialBalance);

            int numberOfReaders = 5;
            int numberOfWriters = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfReaders + numberOfWriters);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReaders + numberOfWriters);
            
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 읽기 작업들
            for (int i = 0; i < numberOfReaders; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 100; j++) {
                            Optional<Balance> balance = balanceRepository.findByUser(user);
                            if (balance.isPresent()) {
                                successfulReads.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Reader error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            // 쓰기 작업들
            for (int i = 0; i < numberOfWriters; i++) {
                final int writerId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 50; j++) {
                            Balance balance = Balance.builder()
                                    .user(user)
                                    .amount(new BigDecimal(String.valueOf(50000 + writerId * 1000 + j)))
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            balanceRepository.save(balance);
                            successfulWrites.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Writer error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successfulReads.get()).isGreaterThan(0);
            assertThat(successfulWrites.get()).isEqualTo(numberOfWriters * 50);
            
            // 최종 상태 확인
            Optional<Balance> finalBalance = balanceRepository.findByUser(user);
            assertThat(finalBalance).isPresent();

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static Stream<Arguments> provideBalanceData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "50000")
        );
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(0L),
                Arguments.of(-1L),
                Arguments.of(-999L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> provideEdgeCaseAmounts() {
        return Stream.of(
                Arguments.of("최소값", "0"),
                Arguments.of("소수점 포함", "100.50"),
                Arguments.of("큰 금액", "999999999"),
                Arguments.of("매우 작은 소수", "0.01")
        );
    }
}