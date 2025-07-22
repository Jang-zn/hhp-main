package kr.hhplus.be.server.unit.usecase;

import kr.hhplus.be.server.domain.entity.Balance;
import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.exception.BalanceException;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import kr.hhplus.be.server.domain.exception.*;

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
        
        static Stream<Arguments> provideChargeData() {
            return Stream.of(
                    Arguments.of(1L, "10000"),
                    Arguments.of(2L, "50000"),
                    Arguments.of(3L, "100000")
            );
        }

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
            
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(userRepositoryPort).findById(userId);
            verify(balanceRepositoryPort).findByUser(user);
            verify(balanceRepositoryPort).save(any(Balance.class));
            verify(cachePort).put(eq("balance:" + userId), any(Balance.class), eq(600));
            verify(lockingPort).releaseLock("balance-" + userId);
        }

        @ParameterizedTest
        @MethodSource("provideChargeData")
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
        
        static Stream<Arguments> provideInvalidUserIds() {
            return Stream.of(
                    Arguments.of(-1L),
                    Arguments.of(0L),
                    Arguments.of(Long.MAX_VALUE),
                    Arguments.of(Long.MIN_VALUE)
            );
        }
    
        static Stream<Arguments> provideInvalidAmounts() {
            return Stream.of(
                    Arguments.of("음수", "-1000"),
                    Arguments.of("영", "0"),
                    Arguments.of("최소값 미만", "999"),
                    Arguments.of("최대값 초과", "1000001"),
                    Arguments.of("매우 큰 값", "999999999")
            );
        }

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
                    .isInstanceOf(Exception.class);
                    
            verify(lockingPort).releaseLock("balance-" + userId);
        }

        @Test
        @DisplayName("실패케이스: null 사용자 ID로 잔액 충전")
        void chargeBalance_WithNullUserId() {
            // given
            Long userId = null;
            BigDecimal chargeAmount = new BigDecimal("50000");

            // when & then
            assertThatThrownBy(() -> chargeBalanceUseCase.execute(userId, chargeAmount))
                    .isInstanceOf(Exception.class);
                    
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
                    .hasMessage(BalanceException.Messages.INVALID_AMOUNT);
                    
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
                    .hasMessage(BalanceException.Messages.INVALID_AMOUNT);
                    
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
                    .hasMessage(BalanceException.Messages.INVALID_AMOUNT);
                    
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
                    .hasMessage(BalanceException.Messages.INVALID_AMOUNT);
                    
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
                    .isInstanceOf(CommonException.ConcurrencyConflict.class)
                    .hasMessage(CommonException.Messages.CONCURRENCY_CONFLICT);
                    
            verify(lockingPort).acquireLock("balance-" + userId);
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
                    .isInstanceOf(CommonException.ConcurrencyConflict.class)
                    .hasMessage(CommonException.Messages.CONCURRENCY_CONFLICT);
            verify(lockingPort).releaseLock("balance-" + userId);
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
            when(lockingPort.acquireLock("balance-" + userId))
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
                        
                    } catch (CommonException.ConcurrencyConflict e) {
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
        
        @Test
        @DisplayName("동시성 테스트: 충전과 사용이 동시에 발생하는 시나리오")
        void chargeBalance_ConcurrentChargeAndUsage() throws InterruptedException {
            // given
            Long userId = 1L;
            BigDecimal initialBalance = new BigDecimal("100000");
            BigDecimal chargeAmount = new BigDecimal("50000");
            BigDecimal usageAmount = new BigDecimal("30000");
            
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger chargeSuccessCount = new AtomicInteger(0);
            AtomicInteger usageSuccessCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            AtomicReference<BigDecimal> finalBalance = new AtomicReference<>();
            
            User user = User.builder()
                    .id(userId)
                    .name("테스트 사용자")
                    .build();
            
            Balance balance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(initialBalance)
                    .build();
            
            // 충전용 락 설정 (충전 프로세스가 먼저 락을 얻는다고 가정)
            when(lockingPort.acquireLock("balance-" + userId))
                    .thenReturn(true)   // 충전 성공
                    .thenReturn(false); // 사용 시 락 실패
            
            // PayOrderUseCase의 결제 락 설정
            when(lockingPort.acquireLock("payment-1"))
                    .thenReturn(true);  // 주문 락은 성공
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.of(balance));
            
            // 충전 후 잔액
            Balance chargedBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(initialBalance.add(chargeAmount))
                    .build();
            
            when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(chargedBalance);
            doNothing().when(lockingPort).releaseLock(anyString());
            doNothing().when(cachePort).put(anyString(), any(Balance.class), anyInt());
            
            // 충전 태스크
            CompletableFuture<Void> chargeTask = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                    Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
                    chargeSuccessCount.incrementAndGet();
                    finalBalance.set(result.getAmount());
                } catch (CommonException.ConcurrencyConflict e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("충전 오류: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }, executor);
            
            // 사용 시뮬레이션 태스크 (PayOrderUseCase의 잔액 락 획득을 모방)
            CompletableFuture<Void> usageTask = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                    // PayOrderUseCase에서 잔액 락 획득 시도
                    if (!lockingPort.acquireLock("balance-" + userId)) {
                        throw new CommonException.ConcurrencyConflict();
                    }
                    // 락 획득 성공 시 해제
                    lockingPort.releaseLock("balance-" + userId);
                    usageSuccessCount.incrementAndGet();
                } catch (CommonException.ConcurrencyConflict e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("사용 오류: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }, executor);
            
            // 동시 실행
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            // 검증: 둘 중 하나만 성공해야 함 (순서는 무관)
            int totalOperations = chargeSuccessCount.get() + usageSuccessCount.get();
            assertThat(totalOperations).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(1);
            
            if (chargeSuccessCount.get() == 1) {
                assertThat(finalBalance.get()).isEqualTo(new BigDecimal("150000"));
            }
            
            // 리소스 정리
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        
        @Test
        @DisplayName("동시성 테스트: 새 잔액 생성 시 동시 충전")
        void chargeBalance_ConcurrentChargeForNewBalance() throws InterruptedException {
            // given
            Long userId = 1L;
            BigDecimal chargeAmount = new BigDecimal("50000");
            
            User user = User.builder()
                    .id(userId)
                    .name("신규 사용자")
                    .build();
            
            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(3);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            
            // 첫 번째 충전만 성공하도록 락 설정
            when(lockingPort.acquireLock("balance-" + userId))
                    .thenReturn(true)   // 첫 번째만 성공
                    .thenReturn(false)  // 나머지는 실패
                    .thenReturn(false);
            
            when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
            when(balanceRepositoryPort.findByUser(user)).thenReturn(Optional.empty()); // 잔액 없음
            
            Balance newBalance = Balance.builder()
                    .id(1L)
                    .user(user)
                    .amount(chargeAmount)
                    .build();
            
            when(balanceRepositoryPort.save(any(Balance.class))).thenReturn(newBalance);
            doNothing().when(lockingPort).releaseLock(anyString());
            doNothing().when(cachePort).put(anyString(), any(Balance.class), anyInt());
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 3개의 동시 충전 시도
            for (int i = 0; i < 3; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
                        successCount.incrementAndGet();
                        assertThat(result.getAmount()).isEqualTo(chargeAmount);
                    } catch (CommonException.ConcurrencyConflict e) {
                        conflictCount.incrementAndGet();
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
            
            // 검증: 하나만 성공, 나머지는 락 충돌
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(2);
            
            // 리소스 정리
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        
        @Test
        @DisplayName("동시성 테스트: 캐시 업데이트 실패 시에도 충전은 성공해야 함")
        void chargeBalance_CacheUpdateFailure() {
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
            doThrow(new RuntimeException("캐시 서버 연결 실패")).when(cachePort).put(anyString(), any(Balance.class), anyInt());
            doNothing().when(lockingPort).releaseLock(anyString());
            
            // when
            Balance result = chargeBalanceUseCase.execute(userId, chargeAmount);
            
            // then - 캐시 실패에도 불구하고 충전은 성공해야 함
            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualTo(new BigDecimal("150000"));
            
            verify(lockingPort).acquireLock("balance-" + userId);
            verify(balanceRepositoryPort).save(any(Balance.class));
            verify(cachePort).put(eq("balance:" + userId), any(Balance.class), eq(600));
            verify(lockingPort).releaseLock("balance-" + userId);
        }
    }
}