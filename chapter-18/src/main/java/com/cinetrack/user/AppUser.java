package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @ToString.Include
    private String username;

    @Column
    private String email;

    public AppUser(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
