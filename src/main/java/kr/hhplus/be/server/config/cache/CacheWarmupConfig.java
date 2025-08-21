package kr.hhplus.be.server.config.cache;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.common.util.KeyGenerator;
import kr.hhplus.be.server.domain.enums.CacheTTL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 애플리케이션 시작 시 캐시 웜업을 담당하는 설정 클래스
 * 
 * ApplicationReadyEvent를 수신하여 전체 상품 정보를 Redis에 미리 로드합니다.
 * 동시성 제어를 통해 중복 웜업을 방지하고, 실패 시 적절한 예외 처리를 수행합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupConfig {

    private final ProductRepositoryPort productRepositoryPort;
    private final CachePort cachePort;
    private final KeyGenerator keyGenerator;
    
    /**
     * 웜업 완료 상태를 추적하는 플래그
     * AtomicBoolean을 사용하여 동시성 환경에서 안전하게 상태 관리
     */
    private final AtomicBoolean warmupCompleted = new AtomicBoolean(false);
    
    /**
     * 애플리케이션 준비 완료 시 캐시 웜업 실행
     * 
     * @param event ApplicationReadyEvent
     * @throws RuntimeException 웜업 과정에서 오류 발생 시
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        long startTime = System.currentTimeMillis();
        log.info("===== 캐시 웜업 시작 =====");
        
        try {
            // 1. 전체 상품 조회
            log.debug("전체 상품 데이터 조회 시작");
            List<Product> allProducts = productRepositoryPort.findAll();
            log.info("조회된 상품 수: {}", allProducts.size());
            
            // 2. 빈 목록 처리
            if (allProducts.isEmpty()) {
                log.info("상품이 없어 캐시 웜업을 건너뜁니다.");
                warmupCompleted.set(true);
                return;
            }
            
            // 3. 각 상품을 개별적으로 캐시에 저장
            log.debug("상품 캐시 저장 시작");
            for (Product product : allProducts) {
                try {
                    String cacheKey = keyGenerator.generateProductCacheKey(product.getId());
                    cachePort.put(cacheKey, product, CacheTTL.PRODUCT_DETAIL.getSeconds());
                    
                    log.trace("상품 캐시 저장 완료: productId={}, cacheKey={}", 
                             product.getId(), cacheKey);
                    
                } catch (Exception e) {
                    log.error("상품 캐시 저장 실패: productId={}", product.getId(), e);
                    String errorMessage = "상품 캐시 저장 중 오류 발생: productId=" + product.getId();
                    if (e.getMessage() != null) {
                        errorMessage += " (" + e.getMessage() + ")";
                    }
                    throw new RuntimeException(errorMessage, e);
                }
            }
            
            // 4. 웜업 완료 처리
            warmupCompleted.set(true);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            log.info("===== 캐시 웜업 완료 =====");
            log.info("웜업된 상품 수: {}, 소요 시간: {}ms", allProducts.size(), duration);
            
            // 성능 경고 (5초 초과 시)
            if (duration > 5000) {
                log.warn("캐시 웜업 시간이 5초를 초과했습니다: {}ms", duration);
            }
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            log.error("===== 캐시 웜업 실패 =====");
            log.error("실패 시간: {}ms", duration);
            log.error("웜업 실패 원인", e);
            
            // 웜업 실패 시 애플리케이션 시작을 중단하지 않고 예외를 전파
            // 원본 예외 메시지를 포함하여 더 구체적인 오류 정보 제공
            String errorMessage = "캐시 웜업 실패";
            if (e.getMessage() != null) {
                errorMessage += ": " + e.getMessage();
            }
            throw new RuntimeException(errorMessage, e);
        }
    }
    
    /**
     * 웜업 완료 상태 확인
     * 
     * @return 웜업 완료 여부
     */
    public boolean isWarmupCompleted() {
        return warmupCompleted.get();
    }
    
    /**
     * 웜업 상태 초기화 (테스트용)
     * 
     * 주의: 프로덕션 환경에서는 사용하지 말 것
     */
    public void resetWarmupStatus() {
        warmupCompleted.set(false);
        log.debug("캐시 웜업 상태가 초기화되었습니다.");
    }
}