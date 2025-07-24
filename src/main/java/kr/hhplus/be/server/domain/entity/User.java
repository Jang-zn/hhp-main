package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
// @Entity
// @Table(name = "users")
public class User extends BaseEntity {

    private String name;

    // @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Balance balance;

    @Builder.Default
    // @OneToMany(mappedBy = "user")
    private List<Order> orders = new ArrayList<>();

    @Builder.Default
    // @OneToMany(mappedBy = "user")
    private List<CouponHistory> couponHistories = new ArrayList<>();
} 