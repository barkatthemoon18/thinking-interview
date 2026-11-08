package com.fuad.interview.models;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "USERS")
@Getter
@Setter
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private int id;

    @Nonnull
    @Column(name = "USERNAME", nullable = false, unique = true, length = 30)
    private String username;

    @OneToMany(mappedBy = "reporter")
    private List<Event> events = new ArrayList<>();

    @OneToMany(mappedBy = "reporter")
    private List<ChangeHistory> changeHistories = new ArrayList<>();
}
