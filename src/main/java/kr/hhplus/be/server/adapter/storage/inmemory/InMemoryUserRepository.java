package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.User;
import kr.hhplus.be.server.domain.port.storage.UserRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepositoryPort {
    
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    
    /**
     * Retrieves a user by their ID from the in-memory store.
     *
     * @param id the unique identifier of the user
     * @return an {@code Optional} containing the user if found, or empty if not present
     */
    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }
    
    /**
     * Stores or updates the given user in the in-memory repository.
     *
     * @param user the user entity to be saved or updated
     * @return the saved user entity
     */
    @Override
    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }
    
    /**
     * Checks whether a user with the specified ID exists in the in-memory repository.
     *
     * @param id the ID of the user to check
     * @return true if a user with the given ID exists, false otherwise
     */
    @Override
    public boolean existsById(Long id) {
        return users.containsKey(id);
    }
} 