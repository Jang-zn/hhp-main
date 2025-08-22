package kr.hhplus.be.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 설정 클래스
 * 
 * datasource.enabled 속성이 false가 아닐 때만 JPA를 활성화합니다.
 * unit 프로파일에서는 이 설정이 비활성화됩니다.
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.datasource.enabled", 
    havingValue = "true", 
    matchIfMissing = true  // 기본값은 true
)
@EnableJpaRepositories(basePackages = "kr.hhplus.be.server.domain.port.storage")
public class JpaConfig {
    // JPA 관련 추가 설정이 필요한 경우 여기에 추가
}