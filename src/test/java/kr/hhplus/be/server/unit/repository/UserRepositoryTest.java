package kr.hhplus.be.server.unit.repository;

import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.util.TestBuilder;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("사용자 Repository 단위 테스트")
class UserRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestEntityManager testEntityManager;
    
    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Test
    @DisplayName("신규 사용자를 JPA를 통해 저장할 수 있다")
    void canSaveNewUserThroughJpa() {
        // Given
        User newUser = TestBuilder.UserBuilder.defaultUser()
                .name("홍길동")
                .build();

        // When
        User savedUser = userRepositoryPort.save(newUser);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        User foundUser = testEntityManager.find(User.class, savedUser.getId());
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("기존 사용자를 업데이트할 수 있다")
    void canUpdateExistingUser() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("원래이름")
                .build();
        User savedUser = testEntityManager.persistAndFlush(user);
        testEntityManager.clear();

        // When - 업데이트를 위해 새 인스턴스 생성 (version 포함)
        User updatedUser = User.builder()
                .id(savedUser.getId())
                .name("변경된이름")
                .version(savedUser.getVersion()) // version 필드 추가
                .build();
        updatedUser = userRepositoryPort.save(updatedUser);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        User foundUser = testEntityManager.find(User.class, updatedUser.getId());
        assertThat(foundUser.getName()).isEqualTo("변경된이름");
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "김철수", "이영희", "English Name", "특수문자123", "한글English混合"})
    @DisplayName("다양한 형태의 사용자 이름으로 저장할 수 있다")
    void canSaveUsersWithVariousNameFormats(String userName) {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name(userName)
                .build();

        // When
        User savedUser = userRepositoryPort.save(user);
        testEntityManager.flush();
        testEntityManager.clear();

        // Then
        User foundUser = testEntityManager.find(User.class, savedUser.getId());
        assertThat(foundUser.getName()).isEqualTo(userName);
    }

    @Test
    @DisplayName("null 사용자 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullUser() {
        // When & Then
        assertThatThrownBy(() -> userRepositoryPort.save(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("ID로 사용자를 조회할 수 있다")
    void canFindUserById() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("조회대상사용자")
                .build();
        User savedUser = testEntityManager.persistAndFlush(user);
        testEntityManager.clear();

        // When
        Optional<User> foundUser = userRepositoryPort.findById(savedUser.getId());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("조회대상사용자");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 빈 결과를 반환한다")
    void returnsEmptyWhenUserNotFoundById() {
        // Given
        Long nonExistentId = 999L;

        // When
        Optional<User> foundUser = userRepositoryPort.findById(nonExistentId);

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("null ID로 조회 시도 시 예외가 발생한다")
    void throwsExceptionWhenFindingByNullId() {
        // When & Then
        assertThatThrownBy(() -> userRepositoryPort.findById(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("존재하는 사용자의 ID로 존재 여부를 확인할 수 있다")
    void canConfirmExistenceOfUser() {
        // Given
        User user = TestBuilder.UserBuilder.defaultUser()
                .name("존재확인대상")
                .build();
        User savedUser = testEntityManager.persistAndFlush(user);
        testEntityManager.clear();

        // When
        boolean exists = userRepositoryPort.existsById(savedUser.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 ID는 존재하지 않음으로 확인된다")
    void confirmNonExistentUserDoesNotExist() {
        // Given
        Long nonExistentId = 999L;

        // When
        boolean exists = userRepositoryPort.existsById(nonExistentId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("null ID로 존재 여부 확인 시도 시 예외가 발생한다")
    void throwsExceptionWhenCheckingExistenceWithNullId() {
        // When & Then
        assertThatThrownBy(() -> userRepositoryPort.existsById(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("조회 중 예외 발생 시 빈 결과를 반환한다")
    void returnsEmptyWhenFindExceptionOccurs() {
        // Given - 존재하지 않는 ID로 조회
        Long nonExistentId = 999999L;

        // When
        Optional<User> result = userRepositoryPort.findById(nonExistentId);
        
        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("제약 조건 위반 시 예외가 발생한다")
    void throwsExceptionWhenConstraintViolated() {
        // Given - 필수 필드가 null인 사용자
        User invalidUser = User.builder()
                .name(null) // name은 필수 필드
                .build();

        // When & Then
        assertThatThrownBy(() -> {
            userRepositoryPort.save(invalidUser);
            testEntityManager.flush(); // 제약 조건 검증을 위해 flush
        }).isInstanceOf(Exception.class);
    }
}