package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
    @NotBlank
    private String productId;

    @PositiveOrZero
    private int salesCount;

    @NotNull
    private LocalDateTime calculatedAt;

    @OneToOne
    @MapsId
    @JoinColumn(name = "product_id")
    @NotNull
    private Product product;
} 