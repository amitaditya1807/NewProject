package com.microservices.auth.model;

import com.microservices.auth.enums.AuthProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "user_auth_providers",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"providerType", "providerUserId"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProviderType providerType;

    @Column(nullable = false)
    private String providerUserId;

    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}