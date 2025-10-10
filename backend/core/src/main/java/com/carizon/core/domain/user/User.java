package com.carizon.core.domain.user;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    @Column(name = "roles")
    private String roles;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected User() {
    }

    public User(String email, String name, List<String> roles) {
        this.email = email;
        this.name = name;
        this.roles = String.join(",", roles);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public List<String> getRoles() {
        if (roles == null || roles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(roles.split(","));
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRoles(List<String> roles) {
        this.roles = String.join(",", roles);
    }
}
