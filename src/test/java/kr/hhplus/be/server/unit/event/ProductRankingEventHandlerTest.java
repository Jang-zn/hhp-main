package kr.hhplus.be.server.unit.event;

import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.domain.event.ProductRankingEventHandler;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProductRankingEventHandler 단위 테스트
 * 
 * Why: 주문 완료 이벤트 처리를 통한 실시간 인기상품 랭킹 업데이트 로직 검증
 * How: 이벤트 수신 시 Redis 랭킹 업데이트가 정확히 수행되는지 확인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductRankingEventHandler 단위 테스트")
class ProductRankingEventHandlerTest {

    @Mock
    private CachePort cachePort;

    @Mock  
    private KeyGenerator keyGenerator;

    @InjectMocks
    private ProductRankingEventHandler productRankingEventHandler;

    private String today;
    private String dailyRankingKey;

    @BeforeEach
    void setUp() {
        today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dailyRankingKey = "ranking:" + today;
    }

    @Test
    @DisplayName("주문 완료 이벤트 처리 시 상품별 랭킹 점수가 정확히 업데이트된다")
    void handleOrderCompleted_UpdatesProductRankingCorrectly() {
        // given
        Long orderId = 1L;
        Long userId = 100L;
        
        List<OrderCompletedEvent.ProductOrderInfo> productOrders = Arrays.asList(
            new OrderCompletedEvent.ProductOrderInfo(201L, 3), // 상품 201: 3개 주문
            new OrderCompletedEvent.ProductOrderInfo(202L, 5), // 상품 202: 5개 주문  
            new OrderCompletedEvent.ProductOrderInfo(203L, 1)  // 상품 203: 1개 주문
        );
        
        OrderCompletedEvent event = new OrderCompletedEvent(orderId, userId, productOrders);
        
        when(keyGenerator.generateDailyRankingKey(today)).thenReturn(dailyRankingKey);
        when(keyGenerator.generateProductRankingKey(201L)).thenReturn("product:201");
        when(keyGenerator.generateProductRankingKey(202L)).thenReturn("product:202");
        when(keyGenerator.generateProductRankingKey(203L)).thenReturn("product:203");

        // when
        productRankingEventHandler.handleOrderCompleted(event);

        // then
        verify(keyGenerator).generateDailyRankingKey(today);
        verify(keyGenerator).generateProductRankingKey(201L);
        verify(keyGenerator).generateProductRankingKey(202L);
        verify(keyGenerator).generateProductRankingKey(203L);
        
        verify(cachePort).addProductScore(dailyRankingKey, "product:201", 3);
        verify(cachePort).addProductScore(dailyRankingKey, "product:202", 5);
        verify(cachePort).addProductScore(dailyRankingKey, "product:203", 1);
    }

    @Test
    @DisplayName("단일 상품 주문 완료 시 해당 상품만 랭킹 업데이트된다")
    void handleOrderCompleted_SingleProduct_UpdatesOnlyThatProduct() {
        // given
        Long orderId = 2L;
        Long userId = 101L;
        
        List<OrderCompletedEvent.ProductOrderInfo> productOrders = Arrays.asList(
            new OrderCompletedEvent.ProductOrderInfo(301L, 10) // 상품 301: 10개 주문
        );
        
        OrderCompletedEvent event = new OrderCompletedEvent(orderId, userId, productOrders);
        
        when(keyGenerator.generateDailyRankingKey(today)).thenReturn(dailyRankingKey);
        when(keyGenerator.generateProductRankingKey(301L)).thenReturn("product:301");

        // when
        productRankingEventHandler.handleOrderCompleted(event);

        // then
        verify(keyGenerator).generateDailyRankingKey(today);
        verify(keyGenerator).generateProductRankingKey(301L);
        verify(cachePort).addProductScore(dailyRankingKey, "product:301", 10);
        
        // 다른 상품은 호출되지 않아야 함
        verify(keyGenerator, times(1)).generateProductRankingKey(any());
        verify(cachePort, times(1)).addProductScore(any(), any(), anyInt());
    }

    @Test
    @DisplayName("빈 상품 목록으로 이벤트 처리 시 랭킹 업데이트가 발생하지 않는다")
    void handleOrderCompleted_EmptyProductList_NoRankingUpdate() {
        // given
        Long orderId = 3L;
        Long userId = 102L;
        
        List<OrderCompletedEvent.ProductOrderInfo> productOrders = Arrays.asList();
        
        OrderCompletedEvent event = new OrderCompletedEvent(orderId, userId, productOrders);
        
        when(keyGenerator.generateDailyRankingKey(today)).thenReturn(dailyRankingKey);

        // when
        productRankingEventHandler.handleOrderCompleted(event);

        // then
        verify(keyGenerator).generateDailyRankingKey(today);
        verify(keyGenerator, never()).generateProductRankingKey(any());
        verify(cachePort, never()).addProductScore(any(), any(), anyInt());
    }

    @Test
    @DisplayName("캐시 포트 예외 발생 시 로깅되고 처리가 종료된다")
    void handleOrderCompleted_CacheException_LoggedAndHandled() {
        // given
        Long orderId = 4L;
        Long userId = 103L;
        
        List<OrderCompletedEvent.ProductOrderInfo> productOrders = Arrays.asList(
            new OrderCompletedEvent.ProductOrderInfo(401L, 2)
        );
        
        OrderCompletedEvent event = new OrderCompletedEvent(orderId, userId, productOrders);
        
        when(keyGenerator.generateDailyRankingKey(today)).thenReturn(dailyRankingKey);
        when(keyGenerator.generateProductRankingKey(401L)).thenReturn("product:401");
        
        // 캐시 예외 발생 시뮬레이션
        doThrow(new RuntimeException("Cache error")).when(cachePort)
            .addProductScore(dailyRankingKey, "product:401", 2);

        // when
        productRankingEventHandler.handleOrderCompleted(event);

        // then - 예외가 발생해도 메서드는 정상 완료되어야 함
        verify(keyGenerator).generateDailyRankingKey(today);
        verify(keyGenerator).generateProductRankingKey(401L);
        verify(cachePort).addProductScore(dailyRankingKey, "product:401", 2);
    }

    @Test
    @DisplayName("여러 번의 주문 완료 이벤트가 누적되어 랭킹에 반영된다")
    void handleOrderCompleted_MultipleEvents_AccumulatesRanking() {
        // given - 첫 번째 주문
        OrderCompletedEvent event1 = new OrderCompletedEvent(1L, 100L, 
            Arrays.asList(new OrderCompletedEvent.ProductOrderInfo(501L, 5)));
            
        // given - 두 번째 주문 (같은 상품)
        OrderCompletedEvent event2 = new OrderCompletedEvent(2L, 101L,
            Arrays.asList(new OrderCompletedEvent.ProductOrderInfo(501L, 3)));
        
        when(keyGenerator.generateDailyRankingKey(today)).thenReturn(dailyRankingKey);
        when(keyGenerator.generateProductRankingKey(501L)).thenReturn("product:501");

        // when
        productRankingEventHandler.handleOrderCompleted(event1);
        productRankingEventHandler.handleOrderCompleted(event2);

        // then
        verify(keyGenerator, times(2)).generateDailyRankingKey(today);
        verify(keyGenerator, times(2)).generateProductRankingKey(501L);
        
        verify(cachePort).addProductScore(dailyRankingKey, "product:501", 5);
        verify(cachePort).addProductScore(dailyRankingKey, "product:501", 3);
    }
}