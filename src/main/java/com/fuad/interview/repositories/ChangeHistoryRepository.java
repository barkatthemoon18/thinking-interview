package com.fuad.interview.repositories;

import com.fuad.interview.models.ChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeHistoryRepository extends JpaRepository<ChangeHistory, Integer> {
    List<ChangeHistory> findAllByEvent_EventIdOrderByChangeDateAscChageHistoryIdAsc(int eventId);
}
