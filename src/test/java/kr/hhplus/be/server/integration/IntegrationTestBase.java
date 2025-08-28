package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.event.EventPort;
import kr.hhplus.be.server.domain.event.OrderCompletedEvent;
import kr.hhplus.be.server.common.util.KeyGenerator;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 베이스 클래스
 * TestContainers를 사용하여 MySQL과 Redis를 자동으로 실행
 * 
 * @Transactional: 테스트 데이터 세팅과 자동 롤백을 위해 사용
 * 실제 구현에서는 @Transactional을 사용하지 않지만,
 * 테스트에서는 데이터 격리와 Repository.save() 트랜잭션을 위해 필요
 */
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("hhplus_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private CachePort cachePort;
    
    @Autowired 
    private RedissonClient redissonClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    /**
     * 각 테스트마다 Redis 캐시를 완전히 정리하여 테스트 간 독립성 보장
     * 
     * evictByPattern("*") 대신 직접 구현하여 모든 Redis 자료구조 타입 처리
     */
    @BeforeEach
    void clearRedisCache() {
        try {
            // 테스트 환경에서만 사용: 모든 Redis 키 삭제
            clearAllRedisKeys();
        } catch (Exception e) {
            // Redis 연결 문제 등으로 실패해도 테스트는 계속 진행
            System.err.println("Redis 캐시 정리 실패 (테스트는 계속 진행): " + e.getMessage());
        }
    }
    
    /**
     * 테스트 환경 전용: Redis의 모든 키를 삭제
     * 
     * RBucket, RScoredSortedSet, RMap, RList 등 모든 자료구조 타입 처리
     */
    private void clearAllRedisKeys() {
        Iterable<String> allKeys = redissonClient.getKeys().getKeysByPattern("*");
        
        int deletedCount = 0;
        for (String key : allKeys) {
            try {
                // 각 자료구조 타입별로 삭제 시도
                if (redissonClient.getBucket(key).delete()) {
                    deletedCount++;
                } else if (redissonClient.getScoredSortedSet(key).delete()) {
                    deletedCount++;
                } else if (redissonClient.getMap(key).delete()) {
                    deletedCount++;
                } else if (redissonClient.getList(key).delete()) {
                    deletedCount++;
                } else if (redissonClient.getSet(key).delete()) {
                    deletedCount++;
                }
            } catch (Exception e) {
                // 개별 키 삭제 실패는 무시하고 계속 진행
                System.err.println("키 삭제 실패: " + key + " - " + e.getMessage());
            }
        }
        
        System.out.println("Redis 테스트 캐시 초기화 완료: " + deletedCount + "개 키 삭제");
    }
    
    @TestConfiguration
    static class TestEventConfiguration {
        
        @Bean
        @Primary
        public EventPort testEventPort(CachePort cachePort, KeyGenerator keyGenerator) {
            return new EventPort() {
                @Override
                public void publish(String topic, Object event) {
                    System.out.println("Test EventPort - publish: topic=" + topic + ", event=" + event.getClass().getSimpleName());
                    
                    // 내부 이벤트: 직접 랭킹 업데이트 처리
                    if (topic.equals("order.completed") && event instanceof OrderCompletedEvent) {
                        handleOrderCompleted((OrderCompletedEvent) event, cachePort, keyGenerator);
                    }
                }

                
                private void handleOrderCompleted(OrderCompletedEvent event, CachePort cachePort, KeyGenerator keyGenerator) {
                    try {
                        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
                        
                        for (OrderCompletedEvent.ProductOrderInfo productOrder : event.getProductOrders()) {
                            Long productId = productOrder.getProductId();
                            int quantity = productOrder.getQuantity();
                            
                            String productKey = keyGenerator.generateProductRankingKey(productId);
                            cachePort.addProductScore(dailyRankingKey, productKey, quantity);
                            
                            System.out.println("Test - Updated product ranking: productId=" + productId + ", quantity=" + quantity);
                        }
                    } catch (Exception e) {
                        System.err.println("Test - Failed to update product ranking: " + e.getMessage());
                    }
                }
            };
        }
    }
}