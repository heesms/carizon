package com.carizon.core.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "platform_listing",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source","sourceKey"}))
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class PlatformListing {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long listingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(nullable = false, length = 64)
    private String sourceKey;

    @Column(nullable = false, length = 40)
    private String myCarKey;

    @Column(length = 20)  private String numberPlate;
    @Column(length = 255) private String title;
    private Integer priceNow;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Status statusNow;

    @Column(nullable = false) private LocalDate firstSeenDate;
    @Column(nullable = false) private LocalDate lastSeenDate;

    public enum Source { ENCAR, CHACHACHA, KCAR, CHUTCHA }
    public enum Status { ACTIVE, PENDING, SOLD, DELISTED }
}
