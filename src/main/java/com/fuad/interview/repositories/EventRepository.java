package com.fuad.interview.repositories;

import com.fuad.interview.models.Event;
import com.fuad.interview.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Integer> {
    List<Event> findAllByReporter(Users reporter);
}
