package com.fuad.interview.repositories;

import com.fuad.interview.models.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Integer> {
    List<Event> findAllByOrderByEventIdAsc();

    List<Event> findAllByReporter_UsernameOrderByEventIdAsc(String username);

    Optional<Event> findByEventIdAndReporter_Username(int eventId, String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.eventId = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") int eventId);
}
