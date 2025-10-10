package com.carizon.core.domain.alert;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "criteria")
    private String criteria;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Alert() {
    }

    public Alert(Long userId, String criteria) {
        this.userId = userId;
        this.criteria = criteria;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCriteria() {
        return criteria;
    }
}
