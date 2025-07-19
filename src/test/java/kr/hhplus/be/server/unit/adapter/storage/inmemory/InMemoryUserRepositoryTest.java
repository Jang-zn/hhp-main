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

import static org.assertj.core.api.Assertions.assertThat;

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
            // when
            Optional<User> foundUser = userRepository.findById(null);

            // then
            assertThat(foundUser).isEmpty();
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
            User user = User.builder().name("사용자1").build();
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
            // when
            boolean exists = userRepository.existsById(null);

            // then
            assertThat(exists).isFalse();
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

    private static Stream<Arguments> provideUserData() {
        return Stream.of(
                Arguments.of("홍길동"),
                Arguments.of("김철수"),
                Arguments.of("이영희")
        );
    }
} 