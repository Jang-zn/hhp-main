package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepositoryPort {
    
    private final Map<String, User> users = new ConcurrentHashMap<>();
    
    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }
    
    @Override
    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }
    
    @Override
    public boolean existsById(String id) {
        return users.containsKey(id);
    }
} 