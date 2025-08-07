package kr.hhplus.be.server.unit.adapter.storage.jpa.user;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.UserJpaRepository;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserJpaRepository 비즈니스 시나리오 테스트
 * 
 * Why: JPA 사용자 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: JPA 기반 사용자 관리 시나리오를 반영한 단위 테스트로 구성
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("JPA 사용자 저장소 비즈니스 시나리오")
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

    // === 사용자 저장 시나리오 ===

    @Test
    @DisplayName("신규 사용자를 JPA를 통해 저장할 수 있다")
    void canSaveNewUserThroughJpa() {
        // Given
        User newUser = TestBuilder.UserBuilder.defaultUser()
                .name("홍길동")
                .build();

        // When
        userJpaRepository.save(newUser);

        // Then
        verify(entityManager).merge(newUser);
    }

    @Test
    @DisplayName("ID가 있는 기존 사용자를 업데이트할 수 있다")
    void canUpdateExistingUserWithId() {
        // Given
        User existingUser = TestBuilder.UserBuilder.defaultUser()
                .id(1L)
                .name("김철수")
                .build();

        // When
        userJpaRepository.save(existingUser);

        // Then
        verify(entityManager).merge(existingUser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "김철수", "이영희", "English Name", "特殊文字", "123숫자"})
    @DisplayName("다양한 형태의 사용자 이름으로 저장할 수 있다")
    void canSaveUsersWithVariousNameFormats(String userName) {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name(userName)
                .build();

        // When
        userJpaRepository.save(user);

        // Then
        verify(entityManager).merge(user);
    }

    @Test
    @DisplayName("null 사용자 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullUser() {
        // When & Then
        assertThatThrownBy(() -> userJpaRepository.save(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        verify(entityManager, never()).merge(any());
    }

    // === 사용자 조회 시나리오 ===

    @Test
    @DisplayName("ID로 사용자를 조회할 수 있다")
    void canFindUserById() {
        // Given
        Long userId = 1L;
        User expectedUser = TestBuilder.UserBuilder.defaultUser()
                .id(userId)
                .name("조회대상사용자")
                .build();
        
        when(entityManager.find(User.class, userId)).thenReturn(expectedUser);

        // When
        Optional<User> foundUser = userJpaRepository.findById(userId);

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(userId);
        assertThat(foundUser.get().getName()).isEqualTo("조회대상사용자");
        
        verify(entityManager).find(User.class, userId);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenUserNotFoundById() {
        // Given
        Long nonExistentId = 999L;
        when(entityManager.find(User.class, nonExistentId)).thenReturn(null);

        // When
        Optional<User> foundUser = userJpaRepository.findById(nonExistentId);

        // Then
        assertThat(foundUser).isEmpty();
        verify(entityManager).find(User.class, nonExistentId);
    }

    @Test
    @DisplayName("null ID로 조회 시도는 예외가 발생한다")
    void throwsExceptionWhenFindingByNullId() {
        // When & Then
        assertThatThrownBy(() -> userJpaRepository.findById(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        verify(entityManager, never()).find(eq(User.class), isNull());
    }

    // === 사용자 존재 여부 확인 시나리오 ===

    @Test
    @DisplayName("존재하는 사용자의 ID로 존재 여부를 확인할 수 있다")
    void canConfirmExistenceOfUser() {
        // Given
        Long userId = 1L;
        when(entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class))
            .thenReturn(countQuery);
        when(countQuery.setParameter("id", userId)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        // When
        boolean exists = userJpaRepository.existsById(userId);

        // Then
        assertThat(exists).isTrue();
        verify(entityManager).createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class);
        verify(countQuery).setParameter("id", userId);
        verify(countQuery).getSingleResult();
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 ID는 존재하지 않음으로 확인된다")
    void confirmNonExistentUserDoesNotExist() {
        // Given
        Long nonExistentId = 999L;
        when(entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class))
            .thenReturn(countQuery);
        when(countQuery.setParameter("id", nonExistentId)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        // When
        boolean exists = userJpaRepository.existsById(nonExistentId);

        // Then
        assertThat(exists).isFalse();
        verify(entityManager).createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class);
        verify(countQuery).setParameter("id", nonExistentId);
        verify(countQuery).getSingleResult();
    }

    @Test
    @DisplayName("null ID로 존재 여부 확인 시도는 예외가 발생한다")
    void throwsExceptionWhenCheckingExistenceWithNullId() {
        // When & Then
        assertThatThrownBy(() -> userJpaRepository.existsById(null))
            .isInstanceOf(IllegalArgumentException.class);
            
        verify(entityManager, never()).createQuery(anyString(), eq(Long.class));
    }

    // === 동시성 시나리오 ===

    @Test
    @DisplayName("동시 저장 요청이 EntityManager를 통해 안전하게 처리된다")
    void safelyHandlesConcurrentSavingThroughEntityManager() {
        // Given
        List<User> testUsers = List.of(
            TestBuilder.UserBuilder.defaultUser().name("동시저장1").build(),
            TestBuilder.UserBuilder.defaultUser().name("동시저장2").build(),
            TestBuilder.UserBuilder.defaultUser().name("동시저장3").build()
        );

        // When - EntityManager 호출 검증을 위한 동시 저장
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(3, () -> {
                User user = testUsers.get((int)(Math.random() * testUsers.size()));
                userJpaRepository.save(user);
                return 1;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(3);
        verify(entityManager, times(3)).merge(any(User.class));
    }

    @Test
    @DisplayName("동시 조회 요청이 EntityManager를 통해 안전하게 처리된다")
    void safelyHandlesConcurrentFindingThroughEntityManager() {
        // Given
        Long userId = 1L;
        User expectedUser = TestBuilder.UserBuilder.defaultUser()
                .id(userId)
                .name("동시조회대상")
                .build();
        when(entityManager.find(User.class, userId)).thenReturn(expectedUser);

        // When - EntityManager 호출 검증을 위한 동시 조회
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(5, () -> {
                Optional<User> found = userJpaRepository.findById(userId);
                return found.isPresent() ? 1 : 0;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getSuccessCount()).isEqualTo(5);
        verify(entityManager, times(5)).find(User.class, userId);
    }

    @Test
    @DisplayName("저장과 조회가 동시에 이루어져도 EntityManager 호출이 정상적으로 처리된다")
    void handlesSimultaneousSaveAndFindOperations() {
        // Given
        User saveUser = TestBuilder.UserBuilder.defaultUser().name("저장용").build();
        User findUser = TestBuilder.UserBuilder.defaultUser().id(1L).name("조회용").build();
        when(entityManager.find(User.class, 1L)).thenReturn(findUser);

        // When - 저장과 조회가 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(6, () -> {
                if (Math.random() < 0.5) {
                    userJpaRepository.save(saveUser);
                    return 1;
                } else {
                    Optional<User> found = userJpaRepository.findById(1L);
                    return found.isPresent() ? 1 : 0;
                }
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(6);
        verify(entityManager, atLeastOnce()).merge(any(User.class));
        verify(entityManager, atLeastOnce()).find(eq(User.class), eq(1L));
    }

    // === 예외 처리 시나리오 ===

    @Test
    @DisplayName("EntityManager에서 예외 발생 시 적절히 전파된다")
    void properlyPropagatesEntityManagerExceptions() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser().name("예외테스트").build();
        RuntimeException expectedException = new RuntimeException("EntityManager 예외");
        doThrow(expectedException).when(entityManager).merge(user);

        // When & Then
        assertThatThrownBy(() -> userJpaRepository.save(user))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("EntityManager 예외");
            
        verify(entityManager).merge(user);
    }

    @Test
    @DisplayName("조회 시 EntityManager 예외가 적절히 전파된다")
    void properlyPropagatesFindExceptions() {
        // Given
        Long userId = 1L;
        RuntimeException expectedException = new RuntimeException("조회 실패");
        when(entityManager.find(User.class, userId)).thenThrow(expectedException);

        // When & Then
        assertThatThrownBy(() -> userJpaRepository.findById(userId))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("조회 실패");
            
        verify(entityManager).find(User.class, userId);
    }

    @Test
    @DisplayName("존재 여부 확인 중 쿼리 실행 예외가 적절히 전파된다")
    void properlyPropagatesExistsQueryExceptions() {
        // Given
        Long userId = 1L;
        RuntimeException expectedException = new RuntimeException("쿼리 실행 실패");
        
        when(entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class))
            .thenReturn(countQuery);
        when(countQuery.setParameter("id", userId)).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenThrow(expectedException);

        // When & Then
        assertThatThrownBy(() -> userJpaRepository.existsById(userId))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("쿼리 실행 실패");
            
        verify(countQuery).getSingleResult();
    }
}