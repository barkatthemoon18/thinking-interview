package com.fuad.interview.service.impl;

import com.fuad.interview.dto.EventDto;
import com.fuad.interview.models.ChangeHistory;
import com.fuad.interview.models.Event;
import com.fuad.interview.repositories.ChangeHistoryRepository;
import com.fuad.interview.repositories.EventRepository;
import com.fuad.interview.repositories.UsersRepository;
import com.fuad.interview.service.EventService;
import com.fuad.interview.service.LoginService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional
@AllArgsConstructor
public class EventServiceImpl implements EventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);
    private final EventRepository eventRepository;
    private final UsersRepository usersRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final LoginService loginService;

    @Override
    public ResponseEntity<String> addEvent(EventDto eventDto, String authorization) {
        var identity = loginService.requireRole(authorization, "reportante");
        Event event;
        ChangeHistory changeHistory;

        if (eventDto == null) {
            LOGGER.error("DTO is null");
            throw new IllegalArgumentException();
        }
        event = new Event();
        if (eventDto.title() != null && !eventDto.title().isBlank() && eventDto.title().length() >= 5 && eventDto.title().length() <= 120) {
            event.setTitle(eventDto.title());
        }
        else {
            LOGGER.error("Title exceeds length bounds");
            throw new IllegalArgumentException();
        }
        if (eventDto.description() != null && !eventDto.description().isBlank() && eventDto.description().length() <= 2000) {
            event.setDescription(eventDto.description());
        }
        else {
            LOGGER.error("Description exceeds length bounds");
            throw new IllegalArgumentException();
        }
        if (eventDto.date() == null) {
            event.setEventDate(LocalDate.now(Clock.systemUTC()));
        }
        else {
            if (eventDto.date().isAfter(LocalDate.now(Clock.systemUTC()))) {
                LOGGER.error("Date is placed in the future");
                throw new IllegalArgumentException("Specified date is future");
            }
            event.setEventDate(eventDto.date());
        }
        if (eventDto.area() == null) {
            LOGGER.error("Area field is null");
            throw new IllegalArgumentException();
        }
        switch (eventDto.area()) {
            case "Planta Norte", "Planta Sur", "Bodega", "Administración":
                event.setEventArea(eventDto.area());
                break;
            default:
                LOGGER.error("Provided are doesn't match any of expected ones: {}", eventDto.area());
                throw new IllegalArgumentException("Wrong area");
        }
        event.setStatus("reportado");
        var user = usersRepository.findByUsername(identity.username());
        if (user == null) {
            throw new IllegalArgumentException("Reporter could not be found");
        }
        event.setReporter(user);
        eventRepository.save(event);
        changeHistory = new ChangeHistory();
        changeHistory.setChangeDate(LocalDateTime.now(Clock.systemUTC()));
        changeHistory.setChangeDescription("Evento creado en estado: REPORTADO");
        changeHistory.setEvent(event);
        changeHistory.setReporter(user);
        changeHistoryRepository.save(changeHistory);
        return new ResponseEntity<>("Event created successfully", HttpStatus.CREATED);
    }
}
