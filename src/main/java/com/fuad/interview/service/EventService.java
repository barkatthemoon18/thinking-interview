package com.fuad.interview.service;

import com.fuad.interview.dto.EventDto;
import com.fuad.interview.dto.ChangeEventStatusDto;
import com.fuad.interview.dto.CorrectiveActionDto;
import com.fuad.interview.dto.CreateCorrectiveActionDto;
import org.springframework.http.ResponseEntity;

public interface EventService {
    ResponseEntity<String> addEvent(EventDto eventDto, String authorization);

    void changeEventStatus(int eventId, ChangeEventStatusDto statusDto, String authorization);

    CorrectiveActionDto createCorrectiveAction(int eventId, CreateCorrectiveActionDto actionDto, String authorization);

    void completeCorrectiveAction(int eventId, int actionId, String authorization);
}
