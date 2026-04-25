package com.cinetrack.user;

import com.cinetrack.watchlist.Watchlist;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Demonstrates {@code @Fetch(FetchMode.SUBSELECT)} on a {@code @OneToMany}.
 *
 * <h2>How SUBSELECT fetch works</h2>
 * When Hibernate first accesses the {@code watchlists} collection for <em>any</em>
 * user in the current persistence context, it loads the collections for
 * <em>all</em> users that were already fetched in that context using a single
 * correlated subquery:
 *
 * <pre>{@code
 * SELECT w.* FROM watchlists w
 * WHERE w.owner_id IN (SELECT id FROM app_users WHERE ...)
 * }</pre>
 *
 * This is ideal when you load a page of users and then always render their
 * watchlists: you get exactly 2 queries total regardless of page size, with no
 * Cartesian product.
 *
 * <h2>Contrast with @BatchSize</h2>
 * {@code @BatchSize} issues ⌈N/batchSize⌉ queries; SUBSELECT always issues
 * exactly 1 collection query per entity type.  SUBSELECT is better when N is
 * large and you always need the collection; {@code @BatchSize} is better when
 * you only sometimes traverse the collection.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String email;

    /**
     * SUBSELECT fetch: one extra query loads watchlists for ALL users
     * currently in the persistence context, not one query per user.
     */
    @OneToMany(mappedBy = "owner", fetch = LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<Watchlist> watchlists = new ArrayList<>();

    public AppUser(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
