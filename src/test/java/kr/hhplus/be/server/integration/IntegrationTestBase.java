package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.domain.port.cache.CachePort;
import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
    
    @Autowired(required = false)
    private EventLogRepositoryPort eventLogRepository;

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
     * 각 테스트마다 Redis 캐시와 EventLog 테이블을 완전히 정리하여 테스트 간 독립성 보장
     * 
     * evictByPattern("*") 대신 직접 구현하여 모든 Redis 자료구조 타입 처리
     */
    @BeforeEach
    void clearTestData() {
        try {
            // 1. 모든 Redis 키 삭제
            clearAllRedisKeys();
        } catch (Exception e) {
            // Redis 연결 문제 등으로 실패해도 테스트는 계속 진행
            System.err.println("Redis 캐시 정리 실패 (테스트는 계속 진행): " + e.getMessage());
        }
        
        try {
            // 2. EventLog 테이블 데이터 삭제 (EventPort 테스트에서 중요)
            clearEventLogData();
        } catch (Exception e) {
            // EventLog 정리 실패해도 테스트는 계속 진행
            System.err.println("EventLog 데이터 정리 실패 (테스트는 계속 진행): " + e.getMessage());
        }
    }
    
    /**
     * 테스트 환경 전용: Redis 통으로 초기화
     * 
     * FLUSHDB로 현재 데이터베이스의 모든 키를 한 번에 삭제
     */
    private void clearAllRedisKeys() {
        try {
            // Redis FLUSHDB로 현재 DB의 모든 키를 한번에 삭제
            redissonClient.getKeys().flushdb();
            System.out.println("Redis 테스트 캐시 FLUSHDB 초기화 완료");
        } catch (Exception e) {
            // FLUSHDB 실패시 기존 방식으로 fallback
            System.err.println("Redis FLUSHDB 실패, 개별 키 삭제로 fallback: " + e.getMessage());
            
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
                    } else if (redissonClient.getStream(key).delete()) {
                        deletedCount++;
                    }
                } catch (Exception ex) {
                    // 개별 키 삭제 실패는 무시하고 계속 진행
                }
            }
            System.out.println("Redis 개별 키 삭제 완료: " + deletedCount + "개 키 삭제");
        }
    }
    
    /**
     * DB 테이블 데이터 완전 정리
     * 
     * 테스트 격리를 위해 모든 테이블의 데이터를 삭제
     */
    private void clearEventLogData() {
        if (eventLogRepository != null) {
            try {
                // EventLog 테이블 완전 초기화 (TRUNCATE 방식)
                long beforeCount = eventLogRepository.count();
                eventLogRepository.deleteAll();
                eventLogRepository.flush(); // 강제로 DB에 반영
                long afterCount = eventLogRepository.count();
                System.out.println("EventLog 테이블 초기화 완료: " + beforeCount + " -> " + afterCount + " 건");
            } catch (Exception e) {
                System.err.println("EventLog 삭제 중 오류: " + e.getMessage());
                // 실패해도 테스트는 계속 진행
            }
        }
    }
    
    // TestEventPort 제거 - 실제 RedisEventAdapter 사용
}