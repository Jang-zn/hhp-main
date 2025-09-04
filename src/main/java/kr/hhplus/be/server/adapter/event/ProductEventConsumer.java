package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.event.ProductUpdatedEvent;
import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 상품 이벤트 처리 Consumer
 * 
 * 상품 생성/수정/삭제 이벤트를 수신하여 캐시를 업데이트합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;

    @KafkaListener(
        topics = {"product.created", "product.updated", "product.deleted"},
        groupId = "product-cache-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleProductEvent(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        Object eventObj = record.value();
        ProductUpdatedEvent event;
        
        try {
            if (eventObj instanceof ProductUpdatedEvent) {
                event = (ProductUpdatedEvent) eventObj;
            } else {
                // ObjectMapper로 변환 시도
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                event = mapper.convertValue(eventObj, ProductUpdatedEvent.class);
            }
        } catch (Exception e) {
            log.error("이벤트 변환 실패: eventObj={}", eventObj, e);
            ack.acknowledge();
            return;
        }
        String topic = record.topic();
        Long productId = event.getProductId();
        
        log.info("상품 이벤트 수신: topic={}, partition={}, offset={}, productId={}, eventType={}", 
                topic, partition, offset, productId, event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case CREATED:
                    handleProductCreated(event);
                    break;
                case UPDATED:
                    handleProductUpdated(event);
                    break;
                case DELETED:
                    handleProductDeleted(event);
                    break;
                case STOCK_UPDATED:
                    handleProductStockUpdated(event);
                    break;
                default:
                    log.warn("알 수 없는 상품 이벤트 타입: {}", event.getEventType());
            }
            
            ack.acknowledge();
            log.info("상품 이벤트 처리 완료: productId={}, eventType={}", productId, event.getEventType());
            
        } catch (Exception e) {
            log.error("상품 이벤트 처리 실패: productId={}, eventType={}", productId, event.getEventType(), e);
            ack.acknowledge(); // 에러 상황에서도 ACK
        }
    }

    private void handleProductCreated(ProductUpdatedEvent event) {
        String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
        
        // 상품 정보를 캐시에 저장
        Product product = createProductFromEvent(event);
        cachePort.put(productCacheKey, product, 3600); // 1시간 TTL
        
        log.debug("상품 생성 이벤트 처리: productId={}, name={}", 
                event.getProductId(), event.getProductName());
    }

    private void handleProductUpdated(ProductUpdatedEvent event) {
        String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
        
        log.info("상품 수정 이벤트 처리 시작: productId={}, name={}, previousName={}", 
                event.getProductId(), event.getProductName(), event.getPreviousName());
        
        // 개별 상품 캐시 업데이트
        Product product = createProductFromEvent(event);
        cachePort.put(productCacheKey, product, 3600);
        
        log.info("캐시 업데이트 완료: key={}, productName={}", productCacheKey, product.getName());
        
        // 관련 목록 캐시들 무효화
        invalidateRelatedCaches(event.getProductId());
        
        log.info("상품 수정 이벤트 처리 완료: productId={}, name={}", 
                event.getProductId(), event.getProductName());
    }

    private void handleProductDeleted(ProductUpdatedEvent event) {
        String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
        
        // 개별 상품 캐시 삭제
        cachePort.evict(productCacheKey);
        
        // 관련 목록 캐시들 무효화
        invalidateRelatedCaches(event.getProductId());
        
        log.debug("상품 삭제 이벤트 처리: productId={}", event.getProductId());
    }

    private void handleProductStockUpdated(ProductUpdatedEvent event) {
        String productCacheKey = keyGenerator.generateProductCacheKey(event.getProductId());
        
        // 개별 상품 캐시만 업데이트 (재고 변경은 목록에 영향 없음)
        Product product = createProductFromEvent(event);
        cachePort.put(productCacheKey, product, 3600);
        
        log.debug("상품 재고 업데이트 이벤트 처리: productId={}, stock={}", 
                event.getProductId(), event.getStock());
    }

    private void invalidateRelatedCaches(Long productId) {
        try {
            // 상품 목록 캐시 무효화 (페이징된 모든 캐시)
            cachePort.evictByPattern("product:list:*");
            
            // 인기 상품 목록 캐시 무효화
            cachePort.evictByPattern("product:popular:*");
            
            log.debug("상품 관련 캐시 무효화 완료: productId={}", productId);
            
        } catch (Exception e) {
            log.warn("상품 관련 캐시 무효화 중 오류 발생: productId={}", productId, e);
        }
    }

    private Product createProductFromEvent(ProductUpdatedEvent event) {
        return Product.builder()
                .id(event.getProductId())
                .name(event.getProductName())
                .price(event.getPrice())
                .stock(event.getStock())
                .build();
    }
}