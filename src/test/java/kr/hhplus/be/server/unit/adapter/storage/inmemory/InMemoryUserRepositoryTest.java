package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryUserRepository;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryUserRepository 단위 테스트")
class InMemoryUserRepositoryTest {

    private InMemoryUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
    }

    @Nested
    @DisplayName("사용자 저장 테스트")
    class SaveTests {
        
        @Test
        @DisplayName("성공케이스: 정상 사용자 저장")
        void save_Success() {
            // given
            User user = User.builder()
                    .id(1L)
                    .name("테스트 사용자")
                    .build();

            // when
            User savedUser = userRepository.save(user);

            // then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getId()).isNotNull();
            assertThat(savedUser.getName()).isEqualTo("테스트 사용자");
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.adapter.storage.inmemory.InMemoryUserRepositoryTest#provideUserData")
        @DisplayName("성공케이스: 다양한 사용자 데이터로 저장")
        void save_WithDifferentUserData(String name) {
            // given
            User user = User.builder()
                    .id(2L)
                    .name(name)
                    .build();

            // when
            User savedUser = userRepository.save(user);

            // then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getName()).isEqualTo(name);
        }

        @Test
        @DisplayName("성공케이스: 빈 이름으로 사용자 저장")
        void save_WithEmptyName() {
            // given
            User user = User.builder()
                    .id(3L)
                    .name("")
                    .build();

            // when
            User savedUser = userRepository.save(user);

            // then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getName()).isEmpty();
        }

        @Test
        @DisplayName("성공케이스: 긴 이름으로 사용자 저장")
        void save_WithLongName() {
            // given
            String longName = "a".repeat(1000);
            User user = User.builder()
                    .id(4L)
                    .name(longName)
                    .build();

            // when
            User savedUser = userRepository.save(user);

            // then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getName()).isEqualTo(longName);
        }
    }

    @Nested
    @DisplayName("사용자 조회 테스트")
    class FindTests {
        
        @Test
        @DisplayName("성공케이스: 사용자 ID로 조회")
        void findById_Success() {
            // given
            User user = User.builder()
                    .id(5L)
                    .name("테스트 사용자")
                    .build();
            User savedUser = userRepository.save(user);

            // when
            Optional<User> foundUser = userRepository.findById(savedUser.getId());

            // then
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getName()).isEqualTo("테스트 사용자");
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 조회")
        void findById_NotFound() {
            // when
            Optional<User> foundUser = userRepository.findById(999L);

            // then
            assertThat(foundUser).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: null ID로 사용자 조회")
        void findById_WithNullId() {
            // when & then
            assertThatThrownBy(() -> userRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("실패케이스: 음수 ID로 사용자 조회")
        void findById_WithNegativeId() {
            // when
            Optional<User> foundUser = userRepository.findById(-1L);

            // then
            assertThat(foundUser).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용자 존재 확인 테스트")
    class ExistsTests {
        
        @Test
        @DisplayName("성공케이스: 사용자 존재 여부 확인")
        void existsById_Success() {
            // given
            User user = User.builder().id(6L).name("사용자1").build();
            User savedUser = userRepository.save(user);

            // when
            boolean exists = userRepository.existsById(savedUser.getId());

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 확인")
        void existsById_NotExists() {
            // when
            boolean exists = userRepository.existsById(999L);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("실패케이스: null ID로 사용자 존재 확인")
        void existsById_WithNullId() {
            // when & then
            assertThatThrownBy(() -> userRepository.existsById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("실패케이스: 음수 ID로 사용자 존재 확인")
        void existsById_WithNegativeId() {
            // when
            boolean exists = userRepository.existsById(-1L);

            // then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 서로 다른 사용자 동시 생성")
        void save_ConcurrentSaveForDifferentUsers() throws Exception {
            // given
            int numberOfUsers = 20;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfUsers);
            AtomicInteger successCount = new AtomicInteger(0);
            // when - 서로 다른 사용자들을 동시에 생성
            for (int i = 0; i < numberOfUsers; i++) {
                final int userIndex = i + 1;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        User user = User.builder()
                                .id((long) userIndex)
                                .name("동시성사용자" + userIndex)
                                .build();
                        
                        userRepository.save(user);
                        Thread.sleep(1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error for user " + userIndex + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then - 모든 사용자가 성공적으로 생성되었는지 확인
            assertThat(successCount.get()).isEqualTo(numberOfUsers);
            
            // 각 사용자가 올바르게 저장되었는지 확인
            for (int i = 1; i <= numberOfUsers; i++) {
                Optional<User> user = userRepository.findById((long) i);
                assertThat(user).isPresent();
                assertThat(user.get().getName()).isEqualTo("동시성사용자" + i);
                assertThat(userRepository.existsById((long) i)).isTrue();
            }

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동일 사용자 동시 업데이트")
        void save_ConcurrentUpdateForSameUser() throws Exception {
            // given
            Long userId = 500L;
            User initialUser = User.builder()
                    .id(userId)
                    .name("초기 사용자")
                    .build();
            userRepository.save(initialUser);

            int numberOfThreads = 5;
            int updatesPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            // when - 동일한 사용자를 동시에 업데이트
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < updatesPerThread; j++) {
                            User updatedUser = User.builder()
                                    .id(userId)
                                    .name("업데이트된 사용자_" + threadId + "_" + j)
                                    .build();
                            
                            userRepository.save(updatedUser);
                            Thread.sleep(1);
                            successfulUpdates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Update error for thread " + threadId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulUpdates.get()).isEqualTo(numberOfThreads * updatesPerThread);
            
            // 최종 상태 확인
            Optional<User> finalUser = userRepository.findById(userId);
            assertThat(finalUser).isPresent();
            assertThat(finalUser.get().getName()).startsWith("업데이트된 사용자_");
            assertThat(userRepository.existsById(userId)).isTrue();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 동시 조회와 저장")
        void concurrentReadAndWrite() throws Exception {
            // given
            User baseUser = User.builder()
                    .id(600L)
                    .name("읽기쓰기 테스트 사용자")
                    .build();
            userRepository.save(baseUser);

            int numberOfReaders = 5;
            int numberOfWriters = 5;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReaders + numberOfWriters);
            
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);
            AtomicInteger successfulExistsChecks = new AtomicInteger(0);
            // 읽기 작업들
            for (int i = 0; i < numberOfReaders; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 10; j++) {
                            Optional<User> user = userRepository.findById(600L);
                            if (user.isPresent()) {
                                successfulReads.incrementAndGet();
                            }
                            
                            if (userRepository.existsById(600L)) {
                                successfulExistsChecks.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Reader error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            // 쓰기 작업들
            for (int i = 0; i < numberOfWriters; i++) {
                final int writerId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 10; j++) {
                            User newUser = User.builder()
                                    .id((long) (700 + writerId * 20 + j))
                                    .name("쓰바이테스트" + writerId + "_" + j)
                                    .build();
                            
                            userRepository.save(newUser);
                            Thread.sleep(1);
                            successfulWrites.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Writer error: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // then
            assertThat(successfulReads.get()).isGreaterThan(0);
            assertThat(successfulWrites.get()).isEqualTo(numberOfWriters * 10);
            assertThat(successfulExistsChecks.get()).isGreaterThan(0);
            
            // 최종 상태 확인
            Optional<User> finalUser = userRepository.findById(600L);
            assertThat(finalUser).isPresent();
            assertThat(userRepository.existsById(600L)).isTrue();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static Stream<Arguments> provideUserData() {
        return Stream.of(
                Arguments.of("홍길동"),
                Arguments.of("김철수"),
                Arguments.of("이영희")
        );
    }
} 