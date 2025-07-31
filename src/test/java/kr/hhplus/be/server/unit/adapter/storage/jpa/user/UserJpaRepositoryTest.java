package kr.hhplus.be.server.unit.adapter.storage.jpa.user;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.UserJpaRepository;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("UserJpaRepository 단위 테스트")
class UserJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> countQuery;

    private UserJpaRepository userJpaRepository;

    @BeforeEach
    void setUp() {
        userJpaRepository = new UserJpaRepository(entityManager);
    }

    @Nested
    @DisplayName("사용자 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 사용자 저장")
        void save_NewUser_Success() {
            // given
            User user = User.builder()
                    .name("테스트 사용자")
                    .createdAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(user);

            // when
            User savedUser = userJpaRepository.save(user);

            // then
            assertThat(savedUser).isEqualTo(user);
            verify(entityManager, times(1)).persist(user);
            verify(entityManager, never()).merge(any());
        }

        @Test
        @DisplayName("성공케이스: 기존 사용자 업데이트")
        void save_ExistingUser_Success() {
            // given
            User user = User.builder()
                    .id(1L)
                    .name("업데이트된 사용자")
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(entityManager.merge(user)).thenReturn(user);

            // when
            User savedUser = userJpaRepository.save(user);

            // then
            assertThat(savedUser).isEqualTo(user);
            verify(entityManager, times(1)).merge(user);
            verify(entityManager, never()).persist(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"홍길동", "김철수", "이영희", "박민수", "최유리"})
        @DisplayName("성공케이스: 다양한 이름의 사용자 저장")
        void save_WithDifferentNames(String name) {
            // given
            User user = User.builder()
                    .name(name)
                    .build();

            doNothing().when(entityManager).persist(user);

            // when
            User savedUser = userJpaRepository.save(user);

            // then
            assertThat(savedUser.getName()).isEqualTo(name);
            verify(entityManager, times(1)).persist(user);
        }
    }

    @Nested
    @DisplayName("ID로 조회 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("성공케이스: ID로 사용자 조회")
        void findById_Success() {
            // given
            Long id = 1L;
            User expectedUser = User.builder()
                    .id(id)
                    .name("테스트 사용자")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(entityManager.find(User.class, id)).thenReturn(expectedUser);

            // when
            Optional<User> foundUser = userJpaRepository.findById(id);

            // then
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get()).isEqualTo(expectedUser);
            assertThat(foundUser.get().getId()).isEqualTo(id);
            assertThat(foundUser.get().getName()).isEqualTo("테스트 사용자");
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 ID로 조회")
        void findById_NotFound() {
            // given
            Long id = 999L;
            when(entityManager.find(User.class, id)).thenReturn(null);

            // when
            Optional<User> foundUser = userJpaRepository.findById(id);

            // then
            assertThat(foundUser).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: 조회 중 예외 발생")
        void findById_Exception() {
            // given
            Long id = 1L;
            when(entityManager.find(User.class, id))
                    .thenThrow(new RuntimeException("데이터베이스 오류"));

            // when
            Optional<User> result = userJpaRepository.findById(id);

            // then
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 100L, 999L, 10000L})
        @DisplayName("실패케이스: 다양한 존재하지 않는 ID로 조회")
        void findById_WithVariousNonExistentIds(Long id) {
            // given
            when(entityManager.find(User.class, id)).thenReturn(null);

            // when
            Optional<User> foundUser = userJpaRepository.findById(id);

            // then
            assertThat(foundUser).isEmpty();
        }
    }

    @Nested
    @DisplayName("존재 여부 확인 테스트")
    class ExistsByIdTests {

        @Test
        @DisplayName("성공케이스: 존재하는 사용자 ID 확인")
        void existsById_UserExists() {
            // given
            Long id = 1L;

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("id", id)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(1L);

            // when
            boolean exists = userJpaRepository.existsById(id);

            // then
            assertThat(exists).isTrue();
            verify(entityManager).createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class);
            verify(countQuery).setParameter("id", id);
        }

        @Test
        @DisplayName("성공케이스: 존재하지 않는 사용자 ID 확인")
        void existsById_UserNotExists() {
            // given
            Long id = 999L;

            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("id", id)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            // when
            boolean exists = userJpaRepository.existsById(id);

            // then
            assertThat(exists).isFalse();
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 10L, 100L, 1000L})
        @DisplayName("성공케이스: 다양한 존재하는 ID들로 확인")
        void existsById_WithVariousExistingIds(Long id) {
            // given
            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("id", id)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(1L);

            // when
            boolean exists = userJpaRepository.existsById(id);

            // then
            assertThat(exists).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, 0L, 99999L})
        @DisplayName("실패케이스: 다양한 존재하지 않는 ID들로 확인")
        void existsById_WithVariousNonExistentIds(Long id) {
            // given
            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter("id", id)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            // when
            boolean exists = userJpaRepository.existsById(id);

            // then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        @Test
        @DisplayName("동시성 테스트: 다중 사용자 생성")
        void save_ConcurrentUserCreation() throws Exception {
            // given
            int numberOfUsers = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfUsers);
            AtomicInteger successCount = new AtomicInteger(0);

            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(entityManager).persist(any(User.class));

            // when
            for (int i = 0; i < numberOfUsers; i++) {
                final int userId = i;
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        User user = User.builder()
                                .name("사용자" + userId)
                                        .createdAt(LocalDateTime.now())
                                .build();
                        
                        userJpaRepository.save(user);
                    } catch (Exception e) {
                        System.err.println("Error creating user " + userId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(numberOfUsers);
            verify(entityManager, times(numberOfUsers)).persist(any(User.class));

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }

        @Test
        @DisplayName("동시성 테스트: 다중 사용자 조회")
        void findById_ConcurrentUserReads() throws Exception {
            // given
            int numberOfReads = 100;
            Long userId = 1L;
            User expectedUser = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();

            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfReads);
            AtomicInteger successfulReads = new AtomicInteger(0);

            when(entityManager.find(User.class, userId)).thenReturn(expectedUser);

            // when
            for (int i = 0; i < numberOfReads; i++) {
                CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Optional<User> foundUser = userJpaRepository.findById(userId);
                        if (foundUser.isPresent()) {
                            successfulReads.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Error reading user: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertThat(successfulReads.get()).isEqualTo(numberOfReads);
            verify(entityManager, times(numberOfReads)).find(User.class, userId);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: persist 중 예외 발생")
        void save_PersistException() {
            // given
            User user = User.builder()
                    .name("테스트 사용자")
                    .build();

            doThrow(new RuntimeException("DB 연결 실패")).when(entityManager).persist(user);

            // when & then
            assertThatThrownBy(() -> userJpaRepository.save(user))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 연결 실패");
        }

        @Test
        @DisplayName("실패케이스: merge 중 예외 발생")
        void save_MergeException() {
            // given
            User user = User.builder()
                    .id(1L)
                    .name("테스트 사용자")
                    .build();

            when(entityManager.merge(user)).thenThrow(new RuntimeException("트랜잭션 오류"));

            // when & then
            assertThatThrownBy(() -> userJpaRepository.save(user))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("트랜잭션 오류");
        }

        @Test
        @DisplayName("실패케이스: 존재 여부 확인 중 예외 발생")
        void existsById_QueryException() {
            // given
            Long id = 1L;

            when(entityManager.createQuery(anyString(), eq(Long.class)))
                    .thenThrow(new RuntimeException("쿼리 실행 오류"));

            // when & then
            assertThatThrownBy(() -> userJpaRepository.existsById(id))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("쿼리 실행 오류");
        }

    }
}