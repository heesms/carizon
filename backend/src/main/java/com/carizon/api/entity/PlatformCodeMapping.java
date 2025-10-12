package com.carizon.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_code_mapping",
       uniqueConstraints = @UniqueConstraint(name = "uq_pcm", 
                                             columnNames = {"platform_name", "level", "raw_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCodeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @Column(name = "platform_name", nullable = false, length = 32)
    private String platformName;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private MappingLevel level;

    @Column(name = "raw_code", length = 128)
    private String rawCode;

    @Column(name = "raw_name", length = 255)
    private String rawName;

    @Column(name = "standard_code", nullable = false, length = 64)
    private String standardCode;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MappingLevel {
        MAKER, MODEL_GROUP, MODEL, TRIM, GRADE
    }
}
