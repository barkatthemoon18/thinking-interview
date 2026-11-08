package com.fuad.interview.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "USERS_ROLES")
@IdClass(UsersRoles.Key.class)
@Getter
@Setter
public class UsersRoles {
    @Id
    @Column(name = "USER_ID", nullable = false)
    private int userId;

    @Id
    @Column(name = "ROLE_ID", nullable = false)
    private int roleId;

    protected UsersRoles() {
        /* Empty intentionally */
    }

    public UsersRoles(int userId, int roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public record Key(int userId, int roleId) { /* Empty */ }
}