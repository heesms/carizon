package com.carizon.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "platform_car",
       uniqueConstraints = @UniqueConstraint(name = "uq_platform_key", 
                                             columnNames = {"platform_name", "platform_car_key"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_car_id")
    private Long platformCarId;

    @Column(name = "platform_name", nullable = false, length = 32)
    private String platformName;

    @Column(name = "platform_car_key", nullable = false, length = 128)
    private String platformCarKey;

    @Column(name = "car_id")
    private Long carId;

    @Column(name = "car_no", length = 32)
    private String carNo;

    @Column(name = "maker_code", length = 32)
    private String makerCode;

    @Column(name = "model_group_code", length = 32)
    private String modelGroupCode;

    @Column(name = "model_code", length = 32)
    private String modelCode;

    @Column(name = "trim_code", length = 32)
    private String trimCode;

    @Column(name = "grade_code", length = 32)
    private String gradeCode;

    @Column(name = "maker_name", length = 64)
    private String makerName;

    @Column(name = "model_group_name", length = 64)
    private String modelGroupName;

    @Column(name = "model_name", length = 64)
    private String modelName;

    @Column(name = "trim_name", length = 64)
    private String trimName;

    @Column(name = "price")
    private Integer price;

    @Column(name = "km")
    private Integer km;

    @Column(name = "displacement")
    private Integer displacement;

    @Column(name = "yymm", length = 6)
    private String yymm;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "color", length = 64)
    private String color;

    @Column(name = "fuel", length = 32)
    private String fuel;

    @Column(name = "transmission", length = 32)
    private String transmission;

    @Column(name = "body_type", length = 64)
    private String bodyType;

    @Column(name = "region", length = 128)
    private String region;

    @Column(name = "m_url", length = 1024)
    private String mUrl;

    @Column(name = "pc_url", length = 1024)
    private String pcUrl;

    @Column(name = "first_ad_day")
    private LocalDate firstAdDay;

    @Column(name = "last_seen_date")
    private LocalDate lastSeenDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra", columnDefinition = "JSON")
    private Map<String, Object> extra;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
