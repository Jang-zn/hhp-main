package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
// @MappedSuperclass
// @EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    
    // @Id
    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // @CreatedDate
    // @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // @LastModifiedDate
    // @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    // @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 