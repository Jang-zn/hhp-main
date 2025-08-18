package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepositoryPort extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
    
    @Query("SELECT o FROM Order o WHERE o.userId = :userId")
    List<Order> findByUserId(@Param("userId") Long userId, Pageable pageable);
    
    Optional<Order> findByIdAndUserId(Long id, Long userId);
} 