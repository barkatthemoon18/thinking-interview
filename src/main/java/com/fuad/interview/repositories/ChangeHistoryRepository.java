package com.fuad.interview.repositories;

import com.fuad.interview.models.ChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeHistoryRepository extends JpaRepository<ChangeHistory, Integer> {
}
