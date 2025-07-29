package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "popular_product_stats")
public class PopularProductStats {

    @Id
    private String productId;

    private int salesCount;

    private LocalDateTime calculatedAt;

    @OneToOne
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;
} 