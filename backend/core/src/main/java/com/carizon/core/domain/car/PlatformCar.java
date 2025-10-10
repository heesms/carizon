package com.carizon.core.domain.car;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_car")
public class PlatformCar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PLATFORM_CAR_ID")
    private Long id;

    @Column(name = "PLATFORM_NAME")
    private String platformName;

    @Column(name = "PLATFORM_CAR_KEY")
    private String platformCarKey;

    @Column(name = "CAR_ID")
    private String carId;

    @Column(name = "PRICE")
    private Integer price;

    @Column(name = "KM")
    private Integer mileage;

    @Column(name = "M_URL")
    private String mobileUrl;

    @Column(name = "PC_URL")
    private String pcUrl;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected PlatformCar() {
    }

    public Long getId() {
        return id;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getPlatformCarKey() {
        return platformCarKey;
    }

    public String getCarId() {
        return carId;
    }

    public Integer getPrice() {
        return price;
    }

    public Integer getMileage() {
        return mileage;
    }

    public String getMobileUrl() {
        return mobileUrl;
    }

    public String getPcUrl() {
        return pcUrl;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
