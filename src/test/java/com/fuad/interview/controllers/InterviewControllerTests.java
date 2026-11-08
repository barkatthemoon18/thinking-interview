package com.fuad.interview.controllers;

import com.fuad.interview.dto.EventDto;
import com.fuad.interview.exceptions.GlobalExceptionHandler;
import com.fuad.interview.models.Event;
import com.fuad.interview.models.Users;
import com.fuad.interview.service.EventService;
import com.fuad.interview.service.LoginService;
import com.fuad.interview.service.UsersService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterviewControllerTests {
    private static final String AUTHORIZATION = "test-token";

    private MockMvc mockMvc;
    private EventService eventService;
    private UsersService usersService;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        usersService = mock(UsersService.class);
        loginService = mock(LoginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InterviewController(eventService, usersService, loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/events", "/api/create-event"})
    void createsEventsThroughTheExistingService(String path) throws Exception {
        when(eventService.addEvent(any(EventDto.class), eq(AUTHORIZATION)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("Event created successfully"));

        mockMvc.perform(post(path).header("Authorization", AUTHORIZATION).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Evento de prueba","description":"Descripcion de prueba",
                         "date":"2026-11-01","area":"Bodega","reporter":"reportante1"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(content().string("Event created successfully"));

        verify(eventService).addEvent(new EventDto("Evento de prueba", "Descripcion de prueba",
                LocalDate.of(2026, 11, 1), "Bodega", "reportante1"), AUTHORIZATION);
    }

    @Test
    void listsEventsWithoutSerializingTheEntityGraph() throws Exception {
        Users reporter = new Users();
        reporter.setId(1);
        reporter.setUsername("reportante1");
        Event event = new Event();
        event.setEventId(4);
        event.setTitle("Evento de prueba");
        event.setDescription("Descripcion de prueba");
        event.setEventDate(LocalDate.of(2026, 11, 1));
        event.setEventArea("Bodega");
        event.setStatus("reportado");
        event.setReporter(reporter);
        reporter.getEvents().add(event);
        when(usersService.viewEvents(AUTHORIZATION)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/events").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(4))
                .andExpect(jsonPath("$[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$[0].reporter").value("reportante1"))
                .andExpect(jsonPath("$[0].correctiveActions").doesNotExist())
                .andExpect(jsonPath("$[0].changeHistories").doesNotExist());

        verify(usersService).viewEvents(AUTHORIZATION);
    }

    @Test
    void requiresAuthorizationToListEvents() throws Exception {
        when(usersService.viewEvents(null))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization es obligatorio."));
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        verify(usersService).viewEvents(null);
    }

    @Test
    void translatesServiceFailuresThroughTheGlobalHandler() throws Exception {
        when(usersService.viewEvents(AUTHORIZATION)).thenThrow(new EntityNotFoundException());
        mockMvc.perform(get("/api/events").header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsMalformedCreationRequestsBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{malformed"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventService);
    }

    @Test
    void exposesThePendingOperationsWithoutReportingSuccess() throws Exception {
        mockMvc.perform(get("/api/events/1").header("Authorization", AUTHORIZATION)).andExpect(status().isNotImplemented());
        mockMvc.perform(patch("/api/events/1/status").header("Authorization", AUTHORIZATION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"en revisión\"}"))
                .andExpect(status().isNotImplemented());
        mockMvc.perform(post("/api/events/1/actions").header("Authorization", AUTHORIZATION).contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"Accion de prueba","responsible":"Nombre Ficticio",
                         "targetDate":"2026-11-15T00:00:00"}
                        """))
                .andExpect(status().isNotImplemented());
        mockMvc.perform(patch("/api/events/1/actions/2/complete").header("Authorization", AUTHORIZATION)).andExpect(status().isNotImplemented());
        verifyNoInteractions(eventService, usersService);
    }

    @Test
    void bindsAndValidatesIdsBeforeDispatchingPendingOperations() throws Exception {
        mockMvc.perform(get("/api/events/invalid")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events/0")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/events/1/actions/0/complete")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/events/1/status").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/events/1/actions").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
