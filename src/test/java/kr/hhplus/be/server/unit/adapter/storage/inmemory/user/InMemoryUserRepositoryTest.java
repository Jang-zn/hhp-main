package kr.hhplus.be.server.unit.adapter.storage.inmemory.user;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryUserRepository;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.UserException;
import kr.hhplus.be.server.util.TestBuilder;
import kr.hhplus.be.server.util.ConcurrencyTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InMemoryUserRepository 비즈니스 시나리오 테스트
 * 
 * Why: 사용자 저장소의 핵심 기능이 비즈니스 요구사항을 충족하는지 검증
 * How: 사용자 관리 시나리오를 반영한 단위 테스트로 구성
 */
@DisplayName("사용자 저장소 비즈니스 시나리오")
class InMemoryUserRepositoryTest {

    private InMemoryUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
    }

    // === 사용자 저장 시나리오 ===
    
    @Test
    @DisplayName("신규 사용자를 정상적으로 저장할 수 있다")
    void canSaveNewUser() {
        // Given
        User newUser = TestBuilder.UserBuilder.defaultUser()
                .name("홍길동")
                .build();

        // When
        User savedUser = userRepository.save(newUser);

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getName()).isEqualTo("홍길동");
    }

    @ParameterizedTest
    @MethodSource("provideUserData")
    @DisplayName("다양한 형태의 사용자 이름으로 저장할 수 있다")
    void canSaveUsersWithVariousNameFormats(String name) {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name(name)
                .build();

        // When
        User savedUser = userRepository.save(user);

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getName()).isEqualTo(name);
    }

    @Test
    @DisplayName("빈 이름을 가진 사용자도 저장할 수 있다")
    void canSaveUserWithEmptyName() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("")
                .build();

        // When
        User savedUser = userRepository.save(user);

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getName()).isEmpty();
    }

    @Test
    @DisplayName("매우 긴 이름을 가진 사용자도 저장할 수 있다")
    void canSaveUserWithVeryLongName() {
        // Given
        String longName = "가".repeat(1000);
        User user = TestBuilder.UserBuilder.defaultUser()
                .name(longName)
                .build();

        // When
        User savedUser = userRepository.save(user);

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getName()).isEqualTo(longName);
        assertThat(savedUser.getName().length()).isEqualTo(1000);
    }

    @Test
    @DisplayName("null 사용자는 저장할 수 없다")
    void cannotSaveNullUser() {
        // When & Then
        assertThatThrownBy(() -> userRepository.save(null))
            .isInstanceOf(UserException.UserCannotBeNull.class);
    }

    // === 사용자 조회 시나리오 ===

    @Test
    @DisplayName("저장된 사용자를 ID로 조회할 수 있다")
    void canFindSavedUserById() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("김철수")
                .build();
        User savedUser = userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findById(savedUser.getId());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("김철수");
        assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenUserNotFound() {
        // When
        Optional<User> foundUser = userRepository.findById(999L);

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("null ID로 조회 시도는 예외가 발생한다")
    void throwsExceptionWhenSearchingWithNullId() {
        // When & Then
        assertThatThrownBy(() -> userRepository.findById(null))
            .isInstanceOf(UserException.UserIdCannotBeNull.class);
    }

    @Test
    @DisplayName("복수의 사용자가 저장된 상태에서 특정 사용자를 정확히 조회할 수 있다")
    void canFindSpecificUserAmongMultiple() {
        // Given
        User user1 = userRepository.save(TestBuilder.UserBuilder.defaultUser().name("사용자1").build());
        User user2 = userRepository.save(TestBuilder.UserBuilder.defaultUser().name("사용자2").build());
        User user3 = userRepository.save(TestBuilder.UserBuilder.defaultUser().name("사용자3").build());

        // When
        Optional<User> foundUser = userRepository.findById(user2.getId());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("사용자2");
        assertThat(foundUser.get().getId()).isEqualTo(user2.getId());
    }

    // === 사용자 존재 여부 확인 시나리오 ===

    @Test
    @DisplayName("저장된 사용자의 존재 여부를 확인할 수 있다")
    void canCheckExistenceOfSavedUser() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("존재확인용사용자")
                .build();
        User savedUser = userRepository.save(user);

        // When
        boolean exists = userRepository.existsById(savedUser.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID는 존재하지 않음으로 확인된다")
    void confirmNonExistentUserDoesNotExist() {
        // When
        boolean exists = userRepository.existsById(999L);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("null ID로 존재 여부 확인 시도는 예외가 발생한다")
    void throwsExceptionWhenCheckingExistenceWithNullId() {
        // When & Then
        assertThatThrownBy(() -> userRepository.existsById(null))
            .isInstanceOf(UserException.UserIdCannotBeNull.class);
    }

    // === 동시성 시나리오 ===

    @Test
    @DisplayName("동시에 여러 사용자를 저장해도 안전하게 처리된다")
    void safelyHandlesConcurrentUserSaving() {
        // When - 10개의 동시 저장 작업
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                User user = TestBuilder.UserBuilder.defaultUser()
                        .name("동시저장사용자_" + System.nanoTime())
                        .build();
                User saved = userRepository.save(user);
                return saved.getId() != null ? 1 : 0;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동시 조회 작업도 안전하게 처리된다")
    void safelyHandlesConcurrentUserQuerying() {
        // Given
        User user = userRepository.save(TestBuilder.UserBuilder.defaultUser().name("동시조회대상").build());

        // When - 10개의 동시 조회 작업
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(10, () -> {
                Optional<User> found = userRepository.findById(user.getId());
                return found.isPresent() ? 1 : 0;
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("사용자 저장과 조회가 동시에 이루어져도 데이터 일관성이 보장된다")
    void maintainsDataConsistencyUnderConcurrentSaveAndFind() {
        // Given
        List<User> savedUsers = List.of(
            userRepository.save(TestBuilder.UserBuilder.defaultUser().name("기존사용자1").build()),
            userRepository.save(TestBuilder.UserBuilder.defaultUser().name("기존사용자2").build())
        );

        // When - 저장과 조회가 동시에 실행
        ConcurrencyTestHelper.ConcurrencyTestResult result = 
            ConcurrencyTestHelper.executeInParallel(8, () -> {
                // 50% 저장, 50% 조회 작업
                if (Math.random() < 0.5) {
                    User newUser = TestBuilder.UserBuilder.defaultUser()
                            .name("신규사용자_" + System.nanoTime())
                            .build();
                    User saved = userRepository.save(newUser);
                    return saved.getId() != null ? 1 : 0;
                } else {
                    User targetUser = savedUsers.get((int)(Math.random() * savedUsers.size()));
                    Optional<User> found = userRepository.findById(targetUser.getId());
                    return found.isPresent() ? 1 : 0;
                }
            });

        // Then
        assertThat(result.getTotalCount()).isEqualTo(8);
        assertThat(result.getSuccessCount()).isEqualTo(8);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    // === 헬퍼 메서드 ===

    static Stream<Arguments> provideUserData() {
        return Stream.of(
            Arguments.of("홍길동"),
            Arguments.of("김철수"),
            Arguments.of("이영희"),
            Arguments.of("박민수"),
            Arguments.of("정수진"),
            Arguments.of("English Name"),
            Arguments.of("123숫자포함"),
            Arguments.of("특수문자!@#"),
            Arguments.of("가나다라마바사아자차카타파하")
        );
    }
}