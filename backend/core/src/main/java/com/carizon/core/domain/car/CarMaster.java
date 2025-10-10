package com.carizon.core.domain.car;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "car_master")
public class CarMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CAR_ID")
    private Long id;

    @Column(name = "CAR_NO")
    private String carNo;

    @Column(name = "MAKER_CODE")
    private String makerCode;

    @Column(name = "MODEL_CODE")
    private String modelCode;

    @Column(name = "TRIM_CODE")
    private String trimCode;

    @Column(name = "GRADE_CODE")
    private String gradeCode;

    @Column(name = "YEAR")
    private Integer year;

    @Column(name = "MILEAGE")
    private Integer mileage;

    @Column(name = "FUEL")
    private String fuel;

    @Column(name = "TRANSMISSION")
    private String transmission;

    @Column(name = "BODY_TYPE")
    private String bodyType;

    @Column(name = "REGION")
    private String region;

    @Column(name = "adv_status")
    private String status;

    @Column(name = "last_seen_date")
    private LocalDate lastSeenDate;

    protected CarMaster() {
    }

    public Long getId() {
        return id;
    }

    public String getCarNo() {
        return carNo;
    }

    public Integer getYear() {
        return year;
    }

    public Integer getMileage() {
        return mileage;
    }

    public String getFuel() {
        return fuel;
    }

    public String getTransmission() {
        return transmission;
    }

    public String getBodyType() {
        return bodyType;
    }

    public String getRegion() {
        return region;
    }

    public String getStatus() {
        return status;
    }
}
