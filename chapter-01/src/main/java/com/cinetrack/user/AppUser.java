package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Application user. Uses a database SEQUENCE for the surrogate key —
 * contrasting with {@link com.cinetrack.movie.Movie} which uses a UUID.
 * Long/sequence PKs are appropriate for high-insert tables where the
 * clustered index behaviour of monotone keys is desirable.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
