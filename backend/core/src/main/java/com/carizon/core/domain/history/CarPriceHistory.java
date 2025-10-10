package com.carizon.core.domain.history;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "car_price_history")
public class CarPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HISTORY_ID")
    private Long id;

    @Column(name = "PLATFORM_CAR_ID")
    private Long platformCarId;

    @Column(name = "PRICE")
    private BigDecimal price;

    @Column(name = "CHECKED_AT")
    private OffsetDateTime checkedAt;

    @Column(name = "is_current")
    private Integer isCurrent;

    protected CarPriceHistory() {
    }

    public Long getId() {
        return id;
    }

    public Long getPlatformCarId() {
        return platformCarId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OffsetDateTime getCheckedAt() {
        return checkedAt;
    }

    public Integer getIsCurrent() {
        return isCurrent;
    }
}
