package com.fuad.interview.controllers;

import com.fuad.interview.dto.ChangeEventStatusDto;
import com.fuad.interview.dto.CreateCorrectiveActionDto;
import com.fuad.interview.dto.EventDto;
import com.fuad.interview.dto.EventSummaryDto;
import com.fuad.interview.dto.EventDetailDto;
import com.fuad.interview.dto.CorrectiveActionDto;
import com.fuad.interview.service.EventService;
import com.fuad.interview.service.UsersService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(InterviewController.class);

    private final EventService eventService;
    private final UsersService usersService;

    @GetMapping("/events")
    public ResponseEntity<List<EventSummaryDto>> getEvents(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LOGGER.info("Solicitud recibida: GET /api/events");
        var events = usersService.viewEvents(authorization);
        LOGGER.info("Solicitud completada: GET /api/events status=200 count={}", events.size());
        return ResponseEntity.ok(events);
    }

    @PostMapping(value = {"/create-event"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createEvent(@RequestBody EventDto eventDto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LOGGER.info("Solicitud recibida: POST /api/create-event");
        var response = eventService.addEvent(eventDto, authorization);
        LOGGER.info("Solicitud completada: POST /api/create-event status={}", response.getStatusCode().value());
        return response;
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventDetailDto> getEvent(@PathVariable("eventId") int eventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LOGGER.info("Solicitud recibida: GET /api/events/{}", eventId);
        var event = usersService.viewEvent(eventId, authorization);
        LOGGER.info("Solicitud completada: GET /api/events/{} status=200", eventId);
        return ResponseEntity.ok(event);
    }

    @PatchMapping(value = "/events/{eventId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changeEventStatus(
            @PathVariable("eventId") int eventId, @RequestBody ChangeEventStatusDto statusDto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LOGGER.info("Solicitud recibida: PATCH /api/events/{}/status", eventId);
        eventService.changeEventStatus(eventId, statusDto, authorization);
        LOGGER.info("Solicitud completada: PATCH /api/events/{}/status status=204", eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/events/{eventId}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CorrectiveActionDto> createCorrectiveAction(
            @PathVariable("eventId") int eventId, @RequestBody CreateCorrectiveActionDto actionDto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LOGGER.info("Solicitud recibida: POST /api/events/{}/actions", eventId);
        var action = eventService.createCorrectiveAction(eventId, actionDto, authorization);
        LOGGER.info("Solicitud completada: POST /api/events/{}/actions status=201 actionId={}",
                eventId, action.actionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(action);
    }

    @PatchMapping("/events/{eventId}/actions/{actionId}/complete")
    public ResponseEntity<Void> completeCorrectiveAction(
            @PathVariable("eventId") int eventId, @PathVariable("actionId") int actionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LOGGER.info("Solicitud recibida: PATCH /api/events/{}/actions/{}/complete", eventId, actionId);
        eventService.completeCorrectiveAction(eventId, actionId, authorization);
        LOGGER.info("Solicitud completada: PATCH /api/events/{}/actions/{}/complete status=204", eventId, actionId);
        return ResponseEntity.noContent().build();
    }
}
