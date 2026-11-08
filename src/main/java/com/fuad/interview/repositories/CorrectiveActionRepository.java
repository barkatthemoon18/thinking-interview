package com.fuad.interview.repositories;

import com.fuad.interview.models.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Integer> {
    List<CorrectiveAction> findAllByEvent_EventIdOrderByActionIdAsc(int eventId);

    Optional<CorrectiveAction> findByActionIdAndEvent_EventId(int actionId, int eventId);

    boolean existsByEvent_EventIdAndStatus(int eventId, String status);
}
