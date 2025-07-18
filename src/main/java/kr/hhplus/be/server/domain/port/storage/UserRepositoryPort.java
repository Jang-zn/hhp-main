package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.User;

import java.util.Optional;

public interface UserRepositoryPort {
    /**
 * Retrieves a user entity by its unique identifier.
 *
 * @param id the unique identifier of the user to retrieve
 * @return an {@code Optional} containing the user if found, or empty if no user exists with the given ID
 */
Optional<User> findById(Long id);
    /**
 * Persists the given user entity and returns the saved instance.
 *
 * @param user the user entity to be saved or updated
 * @return the persisted user entity
 */
User save(User user);
    /**
 * Checks whether a user with the specified ID exists in storage.
 *
 * @param id the unique identifier of the user
 * @return true if a user with the given ID exists, false otherwise
 */
boolean existsById(Long id);
} 