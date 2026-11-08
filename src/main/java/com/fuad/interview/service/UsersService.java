package com.fuad.interview.service;

import com.fuad.interview.dto.EventDetailDto;
import com.fuad.interview.dto.EventSummaryDto;

import java.util.List;


public interface UsersService {
    List<EventSummaryDto> viewEvents(String authorization);

    EventDetailDto viewEvent(int eventId, String authorization);
}
