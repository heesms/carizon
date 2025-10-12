package com.carizon.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "car_master")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(name = "year")
    private Short year;

    @Column(name = "mileage")
    private Integer mileage;

    @Column(name = "color", length = 64)
    private String color;

    @Column(name = "transmission", length = 32)
    private String transmission;

    @Column(name = "fuel", length = 32)
    private String fuel;

    @Column(name = "displacement")
    private Integer displacement;

    @Column(name = "body_type", length = 64)
    private String bodyType;

    @Column(name = "region", length = 128)
    private String region;

    @Column(name = "adv_status", length = 32)
    private String advStatus;

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
