package com.carizon.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_price_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "platform_car_id", nullable = false)
    private Long platformCarId;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
