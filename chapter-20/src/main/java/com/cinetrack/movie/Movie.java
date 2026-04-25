package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A movie entity scoped to a tenant schema.
 *
 * <p>The table name {@code movies} is unqualified — PostgreSQL resolves it
 * against whatever schema is currently active on the connection (set by
 * {@code MultiTenantConnectionProviderImpl} via {@code SET search_path}).
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 50)
    private String genre;

    public Movie(String title, String genre) {
        this.title = title;
        this.genre = genre;
    }
}
