package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Application user entity.
 *
 * <p>The {@code email} field is of type {@link EmailAddress}: a domain value object.
 * Because {@link EmailAddressConverter} is registered with {@code autoApply=true},
 * Hibernate automatically converts it to/from the {@code email} VARCHAR column with
 * no additional annotation required on this field.
 *
 * <p>{@code createdAt} is set by the database DEFAULT expression and is never written
 * by Hibernate ({@code insertable=false, updatable=false}).
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /**
     * Automatically converted by {@link EmailAddressConverter}.
     * Hibernate stores the result of {@code EmailAddress.getValue()} (a plain
     * lowercase string) in the {@code email} column and reconstructs the
     * {@code EmailAddress} object on load.
     */
    @Column(nullable = false, unique = true)
    private EmailAddress email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public AppUser(String username, EmailAddress email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
