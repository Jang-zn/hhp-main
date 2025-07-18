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
    void findByUserId_Success() {
        // given
        User user = User.builder()
                .name("테스트 사용자")
                .build();
        Balance balance = Balance.builder()
                .user(user)
                .amount(new BigDecimal("50000"))
                .build();
        balanceRepository.save(balance);

        // when
        Optional<Balance> foundBalance = balanceRepository.findByUserId(user.getId());

        // then
        assertThat(foundBalance).isPresent();
        assertThat(foundBalance.get().getAmount()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 잔액 조회")
    void findByUserId_NotFound() {
        // when
        Optional<Balance> foundBalance = balanceRepository.findByUserId(999L);

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

    private static Stream<Arguments> provideBalanceData() {
        return Stream.of(
                Arguments.of("홍길동", "100000"),
                Arguments.of("김철수", "250000"),
                Arguments.of("이영희", "50000")
        );
    }
}