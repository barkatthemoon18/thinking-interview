package com.fuad.interview.repositories;

import com.fuad.interview.models.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolesRepository extends JpaRepository<Roles, Integer> {
    @Query("""
            select r.roleName from Roles r, UsersRoles ur
            where r.id = ur.roleId and ur.userId = :userId
            """)
    List<String> findRoleNamesByUserId(@Param("userId") int userId);
}
