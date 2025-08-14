package kr.hhplus.be.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TransactionTemplate 설정
 * 
 * 명시적 트랜잭션 관리를 위한 TransactionTemplate 빈 설정
 * Lock → Transaction → Logic → Transaction End → Lock Release 순서 보장
 */
@Configuration
public class TransactionConfig {

    /**
     * 기본 TransactionTemplate
     * - READ_WRITE 트랜잭션
     * - REQUIRED 전파 속성
     * - 30초 타임아웃
     */
    @Bean
    @Primary
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        
        // 전파 속성: 기존 트랜잭션에 참여하거나 새로 시작
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        
        // 격리 수준: READ_COMMITTED (기본값)
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        // 타임아웃: 30초
        template.setTimeout(30);
        
        // 읽기-쓰기 트랜잭션
        template.setReadOnly(false);
        
        return template;
    }

    /**
     * 읽기 전용 TransactionTemplate
     * - 조회 작업 최적화
     * - SUPPORTS 전파 속성 (트랜잭션 있으면 참여, 없어도 실행)
     */
    @Bean("readOnlyTransactionTemplate")
    public TransactionTemplate readOnlyTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        
        // 읽기 전용 최적화
        template.setReadOnly(true);
        
        // 트랜잭션이 있으면 참여, 없어도 실행
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
        
        // 격리 수준: READ_COMMITTED
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        // 읧기 전용은 짧은 타임아웃
        template.setTimeout(15);
        
        return template;
    }

    /**
     * 새로운 트랜잭션 TransactionTemplate
     * - 독립적인 트랜잭션이 필요한 경우
     * - 로깅, 감사 등에 사용
     */
    @Bean("newTransactionTemplate")
    public TransactionTemplate newTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        
        // 항상 새로운 트랜잭션 시작
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        // 격리 수준: READ_COMMITTED
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        // 독립적인 작업용 짧은 타임아웃
        template.setTimeout(10);
        
        return template;
    }

    /**
     * 긴 작업용 TransactionTemplate
     * - 배치 처리, 대용량 데이터 처리용
     * - 긴 타임아웃 설정
     */
    @Bean("longTransactionTemplate")  
    public TransactionTemplate longTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        // 긴 작업용 2분 타임아웃
        template.setTimeout(120);
        
        return template;
    }
}