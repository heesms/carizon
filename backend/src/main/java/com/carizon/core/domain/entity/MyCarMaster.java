package com.carizon.core.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "my_car_master")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MyCarMaster {

    @Id @Column(length = 40)
    private String myCarKey;

    @Column(nullable = false, unique = true, length = 20)
    private String numberPlate;

    @Column(nullable = false, length = 20)  private String makerCode;
    @Column(nullable = false, length = 50)  private String makerName;
    @Column(nullable = false, length = 20)  private String modelGroupCode;
    @Column(nullable = false, length = 100) private String modelGroupName;
    @Column(nullable = false, length = 20)  private String modelCode;
    @Column(nullable = false, length = 150) private String modelName;
    @Column(length = 20)                    private String trimCode;
    @Column(length = 150)                   private String trimName;
    @Column(length = 20)                    private String gradeCode;
    @Column(length = 150)                   private String gradeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AdvertStatus advertStatus;

    @Column(nullable = false) private LocalDate firstSeenDate;
    @Column(nullable = false) private LocalDate lastSeenDate;

    public enum AdvertStatus { ACTIVE, SOLD, DELISTED }
}
