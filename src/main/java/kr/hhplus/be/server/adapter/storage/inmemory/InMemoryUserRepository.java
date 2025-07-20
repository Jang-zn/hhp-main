package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryUserRepository implements UserRepositoryPort {
    
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    @Override
    public Optional<User> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return Optional.ofNullable(users.get(id));
    }
    
    @Override
    public User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        Long userId = user.getId() != null ? user.getId() : nextId.getAndIncrement();
        
        User savedUser = users.compute(userId, (key, existingUser) -> {
            if (existingUser != null) {
                return User.builder()
                        .id(existingUser.getId())
                        .name(user.getName())
                        .createdAt(existingUser.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build();
            } else {
                return User.builder()
                        .id(userId)
                        .name(user.getName())
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build();
            }
        });
        
        return savedUser;
    }
    
    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return users.containsKey(id);
    }
} 