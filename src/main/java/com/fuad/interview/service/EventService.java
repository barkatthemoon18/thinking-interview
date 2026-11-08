package com.fuad.interview.service;

import com.fuad.interview.dto.EventDto;
import org.springframework.http.ResponseEntity;

public interface EventService {
    ResponseEntity<String> addEvent(EventDto eventDto, String authorization);
}
