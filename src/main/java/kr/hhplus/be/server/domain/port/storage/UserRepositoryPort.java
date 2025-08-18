package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepositoryPort extends JpaRepository<User, Long> {
    // JpaRepository에서 제공하는 기본 메서드들:
    // Optional<User> findById(Long id)
    // User save(User user)
    // boolean existsById(Long id)
    // void deleteById(Long id)
    // List<User> findAll()
} 