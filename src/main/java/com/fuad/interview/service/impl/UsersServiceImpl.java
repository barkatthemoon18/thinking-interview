package com.fuad.interview.service.impl;

import com.fuad.interview.models.Event;
import com.fuad.interview.models.Users;
import com.fuad.interview.repositories.EventRepository;
import com.fuad.interview.repositories.UsersRepository;
import com.fuad.interview.service.UsersService;
import com.fuad.interview.service.LoginService;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class UsersServiceImpl implements UsersService {
    private final UsersRepository usersRepository;
    private final EventRepository eventRepository;
    private final LoginService loginService;

    @Override
    public List<Event> viewEvents(String authorization) {
        var identity = loginService.authenticate(authorization);
        if ("revisor".equals(identity.role())) {
            return eventRepository.findAll();
        }
        Users users;
        users = usersRepository.findByUsername(identity.username());
        if (users == null) {
            throw new EntityNotFoundException("User not found");
        }
        return eventRepository.findAllByReporter(users);
    }
}
