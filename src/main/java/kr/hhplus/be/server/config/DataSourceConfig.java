package kr.hhplus.be.server.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

/**
 * DataSource 설정 클래스
 * 
 * LazyConnectionDataSourceProxy를 사용하여 DB 연결을 쿼리 실행 시점까지 지연시킵니다.
 * 이를 통해 트랜잭션 범위를 최소화하고 연결 풀 효율성을 높입니다.
 */
@Configuration
public class DataSourceConfig {

    /**
     * HikariCP 기본 DataSource 설정
     * application.yml의 spring.datasource.hikari 설정을 바인딩합니다.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource hikariDataSource() {
        return new HikariDataSource();
    }

    /**
     * LazyConnectionDataSourceProxy를 사용한 Primary DataSource
     * 
     * 장점:
     * - 트랜잭션 시작 시 연결을 획득하지 않고, 실제 SQL 실행 직전에 연결 획득
     * - 트랜잭션 내 외부 API 호출 시 연결 풀 점유 시간 최소화
     * - 연결 풀 고갈 위험 감소
     * - MySQL Replication 환경에서 마스터/슬레이브 라우팅 정확도 향상
     */
    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikariDataSource) {
        return new LazyConnectionDataSourceProxy(hikariDataSource);
    }
}