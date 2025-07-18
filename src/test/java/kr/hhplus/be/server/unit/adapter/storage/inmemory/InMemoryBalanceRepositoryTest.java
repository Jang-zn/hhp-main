package kr.hhplus.be.server.unit.adapter.storage.inmemory;

import kr.hhplus.be.server.adapter.storage.inmemory.InMemoryBalanceRepository;
import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
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

    @Test
    @DisplayName("잔액 저장 성공")
    void save_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
                .build();

        // when
        Balance savedBalance = balanceRepository.save(balance);

        // then
        assertThat(savedBalance).isNotNull();
        assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(savedBalance.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("사용자 ID로 잔액 조회 성공")
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
    @DisplayName("존재하지 않는 사용자 잔액 조회")
    void findByUser_NotFound() {
        // given
        User user = User.builder().id(999L).build();

        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

        // then
        assertThat(foundBalance).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideBalanceData")
    @DisplayName("다양한 잔액 데이터로 저장")
    void save_WithDifferentBalanceData(String userName, String amount) {
        // given
        User user = User.builder()
                .name(userName)
                .build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal(amount))
                .build();

        // when
        Balance savedBalance = balanceRepository.save(balance);

        // then
        assertThat(savedBalance).isNotNull();
        assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal(amount));
        assertThat(savedBalance.getUser().getName()).isEqualTo(userName);
    }

    @Test
    @DisplayName("null 사용자 ID로 조회 시 예외 발생")
    void findByUserId_WithNullUserId() {
        // when & then
        assertThatThrownBy(() -> balanceRepository.findByUser(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 사용자 ID로 조회")
    void findByUserId_WithNegativeUserId() {
        // given
        User user = User.builder().id(-1L).name("테스트 사용자").build();
        
        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

        // then
        assertThat(foundBalance).isEmpty();
    }

    @Test
    @DisplayName("null 잔액 객체 저장 시 예외 발생")
    void save_WithNullBalance() {
        // when & then
        assertThatThrownBy(() -> balanceRepository.save(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 사용자가 포함된 잔액 저장 시 예외 발생")
    void save_WithNullUser() {
        // given
        Balance balance = Balance.builder()
                .user(null)
                .amount(new BigDecimal("100000"))
                .build();

        // when & then
        assertThatThrownBy(() -> balanceRepository.save(balance))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("동일 사용자 잔액 업데이트")
    void save_UpdateExistingBalance() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        Balance originalBalance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("50000"))
                .build();
        balanceRepository.save(originalBalance);

        Balance updatedBalance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("100000"))
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
    @MethodSource("provideInvalidUserIds")
    @DisplayName("유효하지 않은 사용자 ID들로 조회")
    void findByUserId_WithInvalidUserIds(Long invalidUserId) {
        // given
        User user = User.builder().id(invalidUserId).name("테스트 사용자").build();
        
        // when
        Optional<Balance> foundBalance = balanceRepository.findByUser(user);

        // then
        assertThat(foundBalance).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideEdgeCaseAmounts")
    @DisplayName("극한값 잔액으로 저장")
    void save_WithEdgeCaseAmounts(String description, String amount) {
        // given
        User user = User.builder()
                .name("엣지케이스 사용자")
                .build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal(amount))
                .build();

        // when
        Balance savedBalance = balanceRepository.save(balance);

        // then
        assertThat(savedBalance).isNotNull();
        assertThat(savedBalance.getAmount()).isEqualTo(new BigDecimal(amount));
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