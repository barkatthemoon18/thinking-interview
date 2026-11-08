package com.fuad.interview.controllers;

import com.fuad.interview.dto.ChangeEventStatusDto;
import com.fuad.interview.dto.CreateCorrectiveActionDto;
import com.fuad.interview.dto.EventDto;
import com.fuad.interview.dto.EventSummaryDto;
import com.fuad.interview.service.EventService;
import com.fuad.interview.service.LoginService;
import com.fuad.interview.service.UsersService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class InterviewController {
    private final EventService eventService;
    private final UsersService usersService;
    private final LoginService loginService;

    @GetMapping("/events")
    public ResponseEntity<List<EventSummaryDto>> getEvents(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        List<EventSummaryDto> events = usersService.viewEvents(authorization).stream()
                .map(event -> new EventSummaryDto(
                        event.getEventId(), event.getTitle(), event.getEventDate(),
                        event.getEventArea(), event.getStatus(), event.getReporter().getUsername()))
                .toList();
        return ResponseEntity.ok(events);
    }

    @PostMapping(value = {"/events", "/create-event"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createEvent(@RequestBody EventDto eventDto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return eventService.addEvent(eventDto, authorization);
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<Void> getEvent(@PathVariable("eventId") int eventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        loginService.authenticate(authorization);
        requirePositiveId(eventId);
        // TODO: delegar la consulta del evento con sus acciones e historial al servicio.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PatchMapping(value = "/events/{eventId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changeEventStatus(
            @PathVariable("eventId") int eventId, @RequestBody ChangeEventStatusDto statusDto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        loginService.requireRole(authorization, "revisor");
        requirePositiveId(eventId);
        // TODO: delegar el cambio de estado y su historial al servicio transaccional.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping(value = "/events/{eventId}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createCorrectiveAction(
            @PathVariable("eventId") int eventId, @RequestBody CreateCorrectiveActionDto actionDto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        loginService.requireRole(authorization, "revisor");
        requirePositiveId(eventId);
        // TODO: delegar la creación de la acción y su historial al servicio transaccional.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PatchMapping("/events/{eventId}/actions/{actionId}/complete")
    public ResponseEntity<Void> completeCorrectiveAction(
            @PathVariable("eventId") int eventId, @PathVariable("actionId") int actionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        loginService.requireRole(authorization, "revisor");
        requirePositiveId(eventId);
        requirePositiveId(actionId);
        // TODO: delegar el completado de la acción y su historial al servicio transaccional.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private void requirePositiveId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }
    }
}
