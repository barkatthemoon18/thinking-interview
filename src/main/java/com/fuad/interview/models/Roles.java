package com.fuad.interview.models;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ROLES")
@Getter
@Setter
public class Roles {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROLE_ID")
    private int id;

    @Nonnull
    @Column(name = "ROLE_NAME", nullable = false, unique = true, length = 30)
    private String roleName;
}
