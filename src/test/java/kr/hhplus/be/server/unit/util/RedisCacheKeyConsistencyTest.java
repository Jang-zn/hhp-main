package kr.hhplus.be.server.unit.util;

import kr.hhplus.be.server.common.util.KeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 키 일관성 검증 테스트
 * 
 * Why: KeyGenerator와 RedisCacheAdapter의 키 생성 방식이 일관되는지 확인
 * How: 생성된 패턴이 실제 저장될 키와 매칭되는지 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 캐시 키 일관성 테스트")
class RedisCacheKeyConsistencyTest {

    private final KeyGenerator keyGenerator = new KeyGenerator();
    
    @Test
    @DisplayName("랭킹 캐시 패턴이 랭킹 키와 일치한다")
    void rankingCachePattern_MatchesRankingKeys() {
        // given
        String today = "2025-01-01";
        String rankingPattern = keyGenerator.generateRankingCachePattern();
        String dailyRankingKey = keyGenerator.generateDailyRankingKey(today);
        
        // when & then - 패턴이 키 구조와 일치하는지 확인
        assertThat(rankingPattern).isEqualTo("product:ranking:*");
        assertThat(dailyRankingKey).isEqualTo("product:ranking:daily:2025-01-01");
        
        // 패턴이 실제 키를 매칭할 수 있는지 확인
        String patternRegex = rankingPattern.replace("*", ".*");
        assertThat(dailyRankingKey).matches(patternRegex);
    }
    
    @Test
    @DisplayName("주간/월간 랭킹 키도 패턴과 매칭된다")
    void weeklyMonthlyRanking_MatchesPattern() {
        // given
        String rankingPattern = keyGenerator.generateRankingCachePattern();
        String weeklyKey = keyGenerator.generateWeeklyRankingKey("2025-W01");
        String monthlyKey = keyGenerator.generateMonthlyRankingKey("2025-01");
        
        // when & then
        String patternRegex = rankingPattern.replace("*", ".*");
        
        assertThat(weeklyKey).matches(patternRegex);
        assertThat(monthlyKey).matches(patternRegex);
        
        assertThat(weeklyKey).isEqualTo("product:ranking:weekly:2025-W01");
        assertThat(monthlyKey).isEqualTo("product:ranking:monthly:2025-01");
    }
    
    @Test
    @DisplayName("다른 도메인 캐시 패턴들도 일관성을 유지한다")
    void otherDomainPatterns_MaintainConsistency() {
        // given
        Long userId = 1L;
        Long productId = 100L;
        
        // when
        String orderPattern = keyGenerator.generateOrderListCachePattern(userId);
        String orderKey = keyGenerator.generateOrderListCacheKey(userId, 10, 0);
        
        String productPattern = keyGenerator.generatePopularProductCachePattern();
        String productKey = keyGenerator.generatePopularProductListCacheKey(7, 20, 0);
        
        // then
        String orderPatternRegex = orderPattern.replace("*", ".*");
        assertThat(orderKey).matches(orderPatternRegex);
        
        String productPatternRegex = productPattern.replace("*", ".*");
        assertThat(productKey).matches(productPatternRegex);
    }
    
    @Test
    @DisplayName("캐시 키 생성 일관성 확인")
    void cacheKeyGeneration_Consistency() {
        // given
        String domain = "product";
        String type = "ranking";
        String identifier = "daily:2025-01-01";
        
        // when
        String customKey = keyGenerator.generateCustomCacheKey(domain, type, identifier);
        String patternKey = keyGenerator.generateRankingCachePattern();
        
        // then
        assertThat(customKey).isEqualTo("product:ranking:daily:2025-01-01");
        assertThat(patternKey).isEqualTo("product:ranking:*");
        
        // 패턴이 커스텀 키를 매칭할 수 있는지 확인
        String patternRegex = patternKey.replace("*", ".*");
        assertThat(customKey).matches(patternRegex);
    }
}