package com.fuad.interview.repositories;

import com.fuad.interview.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<Users, Integer> {
    Users findByUsername(String reporter);
}
