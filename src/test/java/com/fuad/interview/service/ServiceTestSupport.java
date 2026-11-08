package com.fuad.interview.service;

import com.fuad.interview.dto.LoginPayloadDto;
import com.fuad.interview.models.CorrectiveAction;
import com.fuad.interview.models.Event;
import com.fuad.interview.models.Users;
import com.fuad.interview.repositories.ChangeHistoryRepository;
import com.fuad.interview.repositories.CorrectiveActionRepository;
import com.fuad.interview.repositories.EventRepository;
import com.fuad.interview.repositories.RolesRepository;
import com.fuad.interview.repositories.UsersRepository;
import com.fuad.interview.service.impl.EventServiceImpl;
import com.fuad.interview.service.impl.LoginServiceImpl;
import com.fuad.interview.service.impl.UsersServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class ServiceTestSupport {
    static final LocalDate EVENT_DATE = LocalDate.of(2026, 11, 1);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    EventRepository events;
    UsersRepository users;
    RolesRepository roles;
    CorrectiveActionRepository actions;
    ChangeHistoryRepository history;
    Users reporter1;
    Users reporter2;
    Users reviewer;
    EventServiceImpl eventService;
    UsersServiceImpl usersService;

    @BeforeEach
    void setUpServices() {
        events = mock(EventRepository.class);
        users = mock(UsersRepository.class);
        roles = mock(RolesRepository.class);
        actions = mock(CorrectiveActionRepository.class);
        history = mock(ChangeHistoryRepository.class);
        reporter1 = user(1, "reportante1");
        reporter2 = user(2, "reportante2");
        reviewer = user(3, "revisor1");
        when(users.findByUsername("reportante1")).thenReturn(reporter1);
        when(users.findByUsername("reportante2")).thenReturn(reporter2);
        when(users.findByUsername("revisor1")).thenReturn(reviewer);
        when(roles.findRoleNamesByUserId(1)).thenReturn(List.of("reportante"));
        when(roles.findRoleNamesByUserId(2)).thenReturn(List.of("reportante"));
        when(roles.findRoleNamesByUserId(3)).thenReturn(List.of("revisor"));
        when(events.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event.getEventId() == 0) event.setEventId(71);
            return event;
        });
        when(actions.save(any(CorrectiveAction.class))).thenAnswer(invocation -> {
            CorrectiveAction action = invocation.getArgument(0);
            if (action.getActionId() == 0) action.setActionId(91);
            return action;
        });
        // Se usa el servicio de login real: los roles de los repositorios pueden cambiar entre llamadas.
        LoginService login = new LoginServiceImpl(users, roles, JsonMapper.builder().build());
        eventService = new EventServiceImpl(events, users, history, actions, login, CLOCK);
        usersService = new UsersServiceImpl(events, actions, history, login);
    }

    static String token(String username, String role) {
        var payload = new LoginPayloadDto("login", username, role);
        return Base64.getEncoder().encodeToString(JsonMapper.builder().build().writeValueAsBytes(payload));
    }

    static void assertStatus(HttpStatus expected, Executable request) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, request).getStatusCode());
    }

    static Event event(int id, String status, Users reporter) {
        Event event = new Event();
        event.setEventId(id);
        event.setTitle("Evento de prueba");
        event.setDescription("Descripcion ficticia");
        event.setEventDate(EVENT_DATE);
        event.setEventArea("Bodega");
        event.setStatus(status);
        event.setReporter(reporter);
        return event;
    }

    static CorrectiveAction action(int id, Event event, String status) {
        CorrectiveAction action = new CorrectiveAction();
        action.setActionId(id);
        action.setDescription("Accion de prueba");
        action.setResponsible("Nombre Ficticio");
        action.setTargetDate(EVENT_DATE.atStartOfDay());
        action.setStatus(status);
        action.setEvent(event);
        return action;
    }

    private static Users user(int id, String username) {
        Users user = new Users();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
