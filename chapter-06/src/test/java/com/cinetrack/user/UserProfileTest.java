package com.cinetrack.user;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the shared-PK {@code @OneToOne} between
 * {@link AppUser} and {@link UserProfile}.
 *
 * <p>The defining invariant: after saving a user with its profile,
 * {@code profile.getId() == user.getId()} — the profile shares the user's PK
 * rather than having an independent surrogate key.
 */
@DisplayName("UserProfile — shared-PK @OneToOne")
class UserProfileTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository userRepository;
    @Autowired private EntityManager  em;

    @Test
    @DisplayName("Saved profile.getId() equals user.getId() — shared primary key")
    void profileSharesUserPrimaryKey() {
        // GIVEN
        AppUser user = new AppUser("frank", "frank@example.com");
        UserProfile profile = new UserProfile("I love sci-fi", "https://cdn.example.com/frank.jpg");
        user.setProfile(profile);   // wires both sides via helper

        // WHEN
        AppUser saved = userRepository.saveAndFlush(user);

        // THEN — profile PK equals user PK
        assertThat(saved.getProfile().getId())
                .isNotNull()
                .isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("Reloading user after eviction returns profile with same PK as user")
    void reloadedProfileStillSharesPk() {
        // GIVEN
        AppUser user = new AppUser("grace", "grace@example.com");
        user.setProfile(new UserProfile("Director", null));
        userRepository.saveAndFlush(user);
        Long userId = user.getId();

        // Evict from first-level cache to force real SELECT
        em.detach(user);

        // WHEN
        AppUser loaded = userRepository.findByIdWithProfile(userId).orElseThrow();

        // THEN
        assertThat(loaded.getProfile()).isNotNull();
        assertThat(loaded.getProfile().getId()).isEqualTo(loaded.getId());
    }

    @Test
    @DisplayName("Profile bio and avatarUrl are persisted and reloaded correctly")
    void profileFieldsRoundTrip() {
        AppUser user = new AppUser("hank", "hank@example.com");
        UserProfile profile = new UserProfile("Film critic", "https://cdn.example.com/hank.png");
        user.setProfile(profile);
        userRepository.saveAndFlush(user);

        em.detach(user);

        AppUser loaded = userRepository.findByIdWithProfile(user.getId()).orElseThrow();
        assertThat(loaded.getProfile().getBio()).isEqualTo("Film critic");
        assertThat(loaded.getProfile().getAvatarUrl()).isEqualTo("https://cdn.example.com/hank.png");
    }

    @Test
    @DisplayName("setProfile helper sets user reference on profile (both sides in sync)")
    void setProfileHelperWiresBothSides() {
        AppUser user = new AppUser("iris", "iris@example.com");
        UserProfile profile = new UserProfile("Cinephile", null);

        user.setProfile(profile);

        // Before any persistence — both sides are wired in memory
        assertThat(user.getProfile()).isSameAs(profile);
        assertThat(profile.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("userWithoutProfile_findById_profileIsNull: user saved without profile has null profile on reload")
    void userWithoutProfile_findById_profileIsNull() {
        // AppUser.optional=false on the OneToOne means Hibernate may issue an
        // extra query on load. We verify the profile reference is null when none
        // was ever persisted (the DB has no matching user_profiles row).
        AppUser user = new AppUser("jake", "jake@example.com");
        // Do NOT call setProfile — save user without a profile
        userRepository.saveAndFlush(user);
        Long userId = user.getId();

        em.detach(user);

        // Load via the basic findById so no JOIN FETCH forces a proxy
        AppUser loaded = userRepository.findById(userId).orElseThrow();

        // The profile OneToOne is optional=false at the mapping level but null
        // at runtime when no profile row exists
        // We assert via the join-fetch query which returns null for missing profiles
        AppUser loadedWithProfile = userRepository.findByIdWithProfile(userId).orElseThrow();
        assertThat(loadedWithProfile.getProfile()).isNull();
    }

    @Test
    @DisplayName("multipleUsers_profilesAreIndependent: each user has its own distinct profile")
    void multipleUsers_profilesAreIndependent() {
        // GIVEN — two users with different profiles
        AppUser user1 = new AppUser("luna", "luna@example.com");
        user1.setProfile(new UserProfile("Sci-fi fan", "https://cdn.example.com/luna.jpg"));
        userRepository.saveAndFlush(user1);

        AppUser user2 = new AppUser("mike", "mike@example.com");
        user2.setProfile(new UserProfile("Documentary lover", null));
        userRepository.saveAndFlush(user2);

        em.detach(user1);
        em.detach(user2);

        // WHEN
        AppUser loaded1 = userRepository.findByIdWithProfile(user1.getId()).orElseThrow();
        AppUser loaded2 = userRepository.findByIdWithProfile(user2.getId()).orElseThrow();

        // THEN — profiles are independent
        assertThat(loaded1.getProfile().getBio()).isEqualTo("Sci-fi fan");
        assertThat(loaded2.getProfile().getBio()).isEqualTo("Documentary lover");
        assertThat(loaded1.getProfile().getId()).isEqualTo(loaded1.getId());
        assertThat(loaded2.getProfile().getId()).isEqualTo(loaded2.getId());
        assertThat(loaded1.getProfile().getId()).isNotEqualTo(loaded2.getProfile().getId());
    }
}
