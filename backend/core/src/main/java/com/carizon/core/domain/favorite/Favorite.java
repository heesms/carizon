package com.carizon.core.domain.favorite;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "favorite")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "car_id")
    private Long carId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Favorite() {
    }

    public Favorite(Long userId, Long carId) {
        this.userId = userId;
        this.carId = carId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCarId() {
        return carId;
    }
}
