package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User profile entity sharing its primary key with {@link AppUser}.
 *
 * <h2>Shared-PK {@code @OneToOne}: owning side</h2>
 * {@code @MapsId} is the key annotation here. It tells Hibernate:
 * <ol>
 *   <li>Use the PK value from the associated {@link AppUser} as this entity's
 *       own PK: there is no separate sequence or identity column.</li>
 *   <li>The {@code id} field is both the {@code @Id} and the FK to
 *       {@code app_users(id)}.</li>
 * </ol>
 *
 * <p>The result in SQL is a {@code user_profiles} table whose {@code id} column
 * is simultaneously the primary key and a foreign key referencing
 * {@code app_users(id)}. No extra column, no extra index.
 *
 * <h2>Why share the PK?</h2>
 * <ul>
 *   <li>Eliminates a surrogate FK column on the profile table.</li>
 *   <li>Makes it impossible to have an orphan profile row (DB constraint).</li>
 *   <li>Allows loading the profile with a PK lookup using the already-known
 *       user ID: no extra index needed.</li>
 * </ul>
 *
 * <h2>Fetch strategy</h2>
 * The association from {@code AppUser} to {@code UserProfile} is declared
 * {@code fetch = LAZY}. Loading a {@code UserProfile} by ID is efficient
 * because its PK equals the user's PK: a single {@code SELECT} on
 * {@code user_profiles} by primary key.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    /**
     * Shared primary key. The value is copied from {@link AppUser#getId()}
     * by Hibernate when {@code @MapsId} is resolved at flush time.
     * Never set this field manually.
     */
    @Id
    private Long id;

    /**
     * Owning side of the shared-PK OneToOne.
     * {@code @MapsId} with no attribute name maps the entire {@code @Id}.
     * {@code @JoinColumn(name="id")} makes explicit that the FK column is
     * the same column as the PK.
     */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id")
    private AppUser user;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    public UserProfile(String bio, String avatarUrl) {
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }
}
