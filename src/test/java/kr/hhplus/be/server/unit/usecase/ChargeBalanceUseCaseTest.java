package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import kr.hhplus.be.server.domain.port.storage.BalanceRepositoryPort;
import kr.hhplus.be.server.domain.port.locking.LockingPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.usecase.balance.ChargeBalanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import kr.hhplus.be.server.domain.exception.BalanceException;

@DisplayName("ChargeBalanceUseCase 단위 테스트")
class ChargeBalanceUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    
    @Mock
    private BalanceRepositoryPort balanceRepositoryPort;
    
    @Mock
    private LockingPort lockingPort;
    
    @Mock
    private CachePort cachePort;

    private ChargeBalanceUseCase chargeBalanceUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chargeBalanceUseCase = new ChargeBalanceUseCase(
                userRepositoryPort, balanceRepositoryPort, lockingPort, cachePort
        );
    }

    @Nested
    @DisplayName("잔액 충전 성공 테스트")
    class SuccessTests {
        
        @Test
        @DisplayName("성공케이스: 정상 잔액 충전")
        void chargeBalance_Success() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            Balance existingBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("100000"))
                    .build();
            
            Balance updatedBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("150000"))
                    .build();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(existingBalance));
            when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(updatedBalance);
            doNothing().when(cachePort).put(anyString(), any(Balance.class), anyInt());
            doNothing().when(lockingPort).releaseLock(anyString());

            // when
            Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualTo(new BigDecimal("150000"));
            
            verify(lockingPort).acquireLock("balance-charge-" + userId);
            verify(userRepositoryPort).findById(userId);
            verify(balanceRepositoryPort).findByUser(user);
            verify(balanceRepositoryPort).save(any(Balance.class));
            verify(cachePort).put(eq("balance:" + userId), any(Balance.class), eq(600));
            verify(lockingPort).releaseLock("balance-charge-" + userId);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.usecase.ChargeBalanceUseCaseTest#provideChargeData")
        @DisplayName("성공케이스: 다양한 충전 금액으로 테스트")
        void chargeBalance_WithDifferentAmounts(Long userId, String chargeAmount) {
            // given
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            Balance existingBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("50000"))
                    .build();
            
            BigDecimal expectedAmount = new BigDecimal("50000").add(new BigDecimal(chargeAmount));
            Balance updatedBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(expectedAmount)
                    .build();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(existingBalance));
            when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(updatedBalance);
            doNothing().when(cachePort).put(anyString(), any(Balance.class), anyInt());
            doNothing().when(lockingPort).releaseLock(anyString());

            // when
            Balance result = chargeBalanceUseCase.execute(userId, new BigDecimal(chargeAmount));

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualTo(expectedAmount);
        }
    }

    @Nested
    @DisplayName("잔액 충전 실패 테스트")
    class FailureTests {
        
        @Test
        @DisplayName("실패케이스: 존재하지 않는 사용자 잔액 충전")
        void chargeBalance_UserNotFound() {
            // given
            Long userId = 999L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.empty());
            doNothing().when(lockingPort).releaseLock(anyString());

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidUser.class)
                    .hasMessage("Invalid user ID");
                    
            verify(lockingPort).releaseLock("balance-charge-" + userId);
        }

        @Test
        @DisplayName("실패케이스: null 사용자 ID로 잔액 충전")
        void chargeBalance_WithNullUserId() {
            // given
            Long userId = null;
            BigDecimal chargeAmount = new BigDecimal("50000");

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidUser.class)
                    .hasMessage("Invalid user ID");
                    
            // 락 관련 호출이 없어야 함
            verify(lockingPort, never()).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("유효하지 않은 충전 금액 - 음수")
        void chargeBalance_WithNegativeAmount() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("-10000");

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidAmount.class)
                    .hasMessage("Amount must be between 1,000 and 1,000,000");
                    
            // 락 관련 호출이 없어야 함
            verify(lockingPort, never()).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패케이스: 유효하지 않은 충전 금액 - 최대 한도 초과")
        void chargeBalance_WithExcessiveAmount() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("2000000"); // 100만원 초과

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidAmount.class)
                    .hasMessage("Amount must be between 1,000 and 1,000,000");
                    
            // 락 관련 호출이 없어야 함
            verify(lockingPort, never()).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패케이스: 유효하지 않은 충전 금액 - 최소 한도 미만")
        void chargeBalance_WithTooSmallAmount() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("500"); // 1000원 미만

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidAmount.class)
                    .hasMessage("Amount must be between 1,000 and 1,000,000");
                    
            // 락 관련 호출이 없어야 함
            verify(lockingPort, never()).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("실패케이스: null 충전 금액")
        void chargeBalance_WithNullAmount() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = null;

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidAmount.class)
                    .hasMessage("Amount must be between 1,000 and 1,000,000");
                    
            // 락 관련 호출이 없어야 함
            verify(lockingPort, never()).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
        }

        @Test
        @DisplayName("동시성 충돌 시나리오 - 락 획득 실패")
        void chargeBalance_LockAcquisitionFailed() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(lockingPort.acquireLock(anyString())).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.ConcurrencyConflict.class)
                    .hasMessage("Concurrent balance update conflict");
                    
            verify(lockingPort).acquireLock("balance-charge-" + userId);
            verify(lockingPort, never()).releaseLock(anyString());
            verify(userRepositoryPort, never()).findById(any());
        }
        
        @Test
        @DisplayName("동시성 충돌 시나리오 - 저장 중 예외")
        void chargeBalance_SaveException() {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            Balance existingBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("100000"))
                    .build();
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(existingBalance));
            when(balanceRepositoryPort.save(any(Balance.class))).thenThrow(new RuntimeException("DB 에러"));
            doNothing().when(lockingPort).releaseLock(anyString());

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.ConcurrencyConflict.class)
                    .hasMessage("Concurrent balance update conflict");
                    
            verify(lockingPort).releaseLock("balance-charge-" + userId);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.usecase.ChargeBalanceUseCaseTest#provideInvalidUserIds")
        @DisplayName("실패케이스: 다양한 비정상 사용자 ID 테스트")
        void chargeBalance_WithInvalidUserIds(Long invalidUserId) {
            // given
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            when(userRepositoryPort.findById(invalidUserId)).thenReturn(Optional.empty());
            doNothing().when(lockingPort).releaseLock(anyString());

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(invalidUserId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidUser.class)
                    .hasMessage("Invalid user ID");
                    
            verify(lockingPort).releaseLock("balance-charge-" + invalidUserId);
        }

        @ParameterizedTest
        @MethodSource("kr.hhplus.be.server.unit.usecase.ChargeBalanceUseCaseTest#provideInvalidAmounts")
        @DisplayName("실패케이스: 다양한 비정상 충전 금액 테스트")
        void chargeBalance_WithInvalidAmounts(String description, String invalidAmount) {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal(invalidAmount);

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(BalanceException.InvalidAmount.class)
                    .hasMessage("Amount must be between 1,000 and 1,000,000");
                    
            // 락 관련 호출이 없어야 함
            verify(lockingPort, never()).acquireLock(anyString());
            verify(lockingPort, never()).releaseLock(anyString());
        }
    }
    
    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동시성 테스트: 다른 사용자들의 동시 충전")
        void chargeBalance_ConcurrentChargesForDifferentUsers() throws InterruptedException {
            // given
            int numberOfUsers = 10;
            BigDecimal chargeAmount = new BigDecimal("10000");
            ExecutorService executor = Executors.newFixedThreadPool(numberOfUsers);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfUsers);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            // 모킹 설정
            when(lockingPort.acquireLock(anyString())).thenReturn(true);
            doNothing().when(lockingPort).releaseLock(anyString());
            doNothing().when(cachePort).put(anyString(), any(Balance.class), anyInt());
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 다수 사용자 동시 충전
            for (int i = 1; i <= numberOfUsers; i++) {
                final Long userId = (long) i;
                
                User user = User.builder()
                        .id(userId)
                        .name("사용자" + userId)
                        .build();
                
                Balance existingBalance = Balance.builder()
                        .id(userId)
                        .user(user)
                        .amount(new BigDecimal("50000"))
                        .build();
                
                Balance updatedBalance = Balance.builder()
                        .id(userId)
                        .user(user)
                        .amount(new BigDecimal("60000"))
                        .build();
                
                when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
                when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(existingBalance));
                when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(updatedBalance);
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await(); // 동시 시작 대기
                        
                        Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
                        
                        assertThat(result).isNotNull();
                        assertThat(result.getAmount()).isEqualTo(new BigDecimal("60000"));
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.err.println("충전 실패 - 사용자: " + userId + ", 오류: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 동시 실행
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            // 검증
            assertThat(successCount.get()).isEqualTo(numberOfUsers);
            assertThat(errorCount.get()).isEqualTo(0);
            
            // 리소스 정리
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        
        @Test
        @DisplayName("동시성 테스트: 같은 사용자의 동시 충전 - 락 경합")
        void chargeBalance_ConcurrentChargesForSameUser() throws InterruptedException {
            // given
            Long userId = 1L;
            int numberOfCharges = 5;
            BigDecimal chargeAmount = new BigDecimal("10000");
            ExecutorService executor = Executors.newFixedThreadPool(numberOfCharges);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numberOfCharges);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger lockFailureCount = new AtomicInteger(0);
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            Balance existingBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("50000"))
                    .build();
            
            Balance updatedBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(new BigDecimal("60000"))
                    .build();
            
            // 락 경합 시나리오: 첫 번째만 성공, 나머지는 락 획득 실패
            when(lockingPort.acquireLock("balance-charge-" + userId))
                    .thenReturn(true)  // 첫 번째 호출만 성공
                    .thenReturn(false) // 나머지는 실패
                    .thenReturn(false)
                    .thenReturn(false)
                    .thenReturn(false);
            
            doNothing().when(lockingPort).releaseLock(anyString());
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(existingBalance));
            when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(updatedBalance);
            doNothing().when(cachePort).put(anyString(), any(Balance.class), anyInt());
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 같은 사용자에 대한 동시 충전
            for (int i = 0; i < numberOfCharges; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        
                        Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
                        successCount.incrementAndGet();
                        
                    } catch (BalanceException.ConcurrencyConflict e) {
                        lockFailureCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("예상치 못한 오류: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 동시 실행
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            // 검증: 하나만 성공, 나머지는 락 실패
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(lockFailureCount.get()).isEqualTo(numberOfCharges - 1);
            
            // 리소스 정리
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    private static Stream<Arguments> provideChargeData() {
        return Stream.of(
                Arguments.of(1L, "10000"),
                Arguments.of(2L, "50000"),
                Arguments.of(3L, "100000")
        );
    }

    private static Stream<Arguments> provideInvalidUserIds() {
        return Stream.of(
                Arguments.of(-1L),
                Arguments.of(0L),
                Arguments.of(Long.MAX_VALUE),
                Arguments.of(Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> provideInvalidAmounts() {
        return Stream.of(
                Arguments.of("음수", "-1000"),
                Arguments.of("영", "0"),
                Arguments.of("최소값 미만", "999"),
                Arguments.of("최대값 초과", "1000001"),
                Arguments.of("매우 큰 값", "999999999")
        );
    }
}