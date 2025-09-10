package kr.hhplus.be.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		// MySQL 드라이버를 명시적으로 등록하여 클래스패스 로딩 문제 해결
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			System.err.println("MySQL 드라이버를 찾을 수 없습니다: " + e.getMessage());
		}
		
		SpringApplication.run(ServerApplication.class, args);
	}

}
