package kr.hhplus.admin;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Admin Server 애플리케이션
 * 
 * HH Plus 이커머스 시스템의 모니터링을 위한 Spring Boot Admin 대시보드를 제공한다.
 * http://localhost:9090에서 웹 UI를 통해 애플리케이션 상태를 실시간으로 모니터링할 수 있다.
 */
@SpringBootApplication
@EnableAdminServer
public class AdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServerApplication.class, args);
    }
}
