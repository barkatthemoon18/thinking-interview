package com.fuad.interview.service;

import com.fuad.interview.models.Event;

import java.util.List;


public interface UsersService {
    List<Event> viewEvents(String authorization);
}
