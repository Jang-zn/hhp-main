package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.hhplus.be.server.domain.exception.UserException;

@Repository
@Profile("test_inmemory")
public class InMemoryUserRepository implements UserRepositoryPort {
    
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    
    public void clear() {
        users.clear();
        nextId.set(1L);
    }
    
    @Override
    public Optional<User> findById(Long id) {
        if (id == null) {
            throw new UserException.UserIdCannotBeNull();
        }
        return Optional.ofNullable(users.get(id));
    }
    
    @Override
    public User save(User user) {
        if (user == null) {
            throw new UserException.UserCannotBeNull();
        }
        if (user.getName() == null) {
            throw new UserException.UserNameCannotBeNull();
        }
        
        Long userId = user.getId() != null ? user.getId() : nextId.getAndIncrement();
        
        User savedUser = users.compute(userId, (key, existingUser) -> {
            if (existingUser != null) {
                user.onUpdate();
                user.setId(existingUser.getId());
                user.setCreatedAt(existingUser.getCreatedAt());
                return user;
            } else {
                user.onCreate();
                if (user.getId() == null) {
                    user.setId(userId);
                }
                return user;
            }
        });
        
        return savedUser;
    }
    
    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            throw new UserException.UserIdCannotBeNull();
        }
        return users.containsKey(id);
    }
} 