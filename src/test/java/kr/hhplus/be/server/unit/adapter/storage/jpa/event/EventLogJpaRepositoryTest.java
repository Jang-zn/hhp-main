package kr.hhplus.be.server.unit.adapter.storage.jpa.event;

import kr.hhplus.be.server.adapter.storage.jpa.EventLogJpaRepository;
import kr.hhplus.be.server.domain.entity.EventLog;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("이벤트 로그 데이터 저장소 비즈니스 시나리오")
class EventLogJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestEntityManager testEntityManager;
    
    private EventLogJpaRepository eventLogJpaRepository;

    @BeforeEach
    void setUp() {
        eventLogJpaRepository = new EventLogJpaRepository(testEntityManager.getEntityManager());
    }

    @Test
    @DisplayName("EventLogJpaRepository가 정상적으로 초기화된다")
    void canInitializeEventLogJpaRepository() {
        // When & Then
        assertThat(eventLogJpaRepository).isNotNull();
    }

    @Test
    @DisplayName("null 이벤트 로그 저장 시도는 예외가 발생한다")
    void throwsExceptionWhenSavingNullEventLog() {
        // When & Then
        assertThatThrownBy(() -> eventLogJpaRepository.save(null))
                .isInstanceOf(Exception.class);
    }
}