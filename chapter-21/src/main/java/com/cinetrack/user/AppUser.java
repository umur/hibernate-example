package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Application user. Intentionally NOT annotated with {@code @Audited}.
 *
 * <p>User records are managed through a separate identity system in production;
 * change history for user accounts is tracked there rather than in Envers.
 * This also demonstrates that {@code @Audited} is opt-in per entity: its
 * absence does not prevent other entities from being audited.
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

    @Column(length = 255)
    private String email;

    public AppUser(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
