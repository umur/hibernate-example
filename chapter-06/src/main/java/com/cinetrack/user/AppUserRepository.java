package com.cinetrack.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    /** Loads user together with profile in one JOIN to avoid a second SELECT. */
    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile WHERE u.id = :id")
    Optional<AppUser> findByIdWithProfile(Long id);
}
