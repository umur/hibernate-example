package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Application user with a shared-PK {@code @OneToOne} relationship to {@link UserProfile}.
 *
 * <h2>Shared-PK {@code @OneToOne}</h2>
 * Rather than giving {@code UserProfile} its own surrogate key and a separate FK
 * column, we share the primary key: the profile row's PK equals the user row's PK.
 * This is modelled with {@code @MapsId} on the profile side (see {@link UserProfile}).
 *
 * <p>On this (inverse) side:
 * <ul>
 *   <li>{@code mappedBy = "user"} — {@code UserProfile.user} owns the relationship.</li>
 *   <li>{@code cascade = CascadeType.ALL} — saving/deleting a user cascades to the
 *       profile automatically.</li>
 *   <li>{@code fetch = FetchType.LAZY} — the profile is not loaded until accessed.
 *       With bytecode enhancement this is truly lazy even for {@code @OneToOne};
 *       without enhancement Hibernate may issue an extra query to check for null.</li>
 *   <li>{@code optional = true} (default) — a profile is not required for every
 *       user. Hibernate must verify existence on load rather than assuming a row
 *       is always present.</li>
 * </ul>
 *
 * <h2>Helper method</h2>
 * {@link #setProfile} wires both sides of the bidirectional association in one call,
 * preventing the common bug of having only one side set within the same session.
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

    @Column(nullable = false, unique = true)
    private String email;

    @OneToOne(mappedBy = "user",
              cascade = CascadeType.ALL,
              fetch = FetchType.LAZY)
    private UserProfile profile;

    public AppUser(String username, String email) {
        this.username = username;
        this.email = email;
    }

    /**
     * Bidirectional sync helper.
     * Call this instead of setting {@code profile} and {@code profile.user} separately.
     *
     * @param profile the profile to associate; must not be null
     */
    public void setProfile(UserProfile profile) {
        this.profile = profile;
        profile.setUser(this);
    }
}
