package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.User;

import java.util.Optional;

public interface UserRepositoryPort {
    Optional<User> findById(String id);
    User save(User user);
    boolean existsById(String id);
} 