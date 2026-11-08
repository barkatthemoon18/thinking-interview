package com.fuad.interview.service.impl;

import com.fuad.interview.dto.ChangeHistoryDto;
import com.fuad.interview.dto.CorrectiveActionDto;
import com.fuad.interview.dto.EventDetailDto;
import com.fuad.interview.dto.EventSummaryDto;
import com.fuad.interview.models.Event;
import com.fuad.interview.repositories.ChangeHistoryRepository;
import com.fuad.interview.repositories.CorrectiveActionRepository;
import com.fuad.interview.repositories.EventRepository;
import com.fuad.interview.service.LoginService;
import com.fuad.interview.service.UsersService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class UsersServiceImpl implements UsersService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsersServiceImpl.class);

    private final EventRepository eventRepository;
    private final CorrectiveActionRepository correctiveActionRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final LoginService loginService;

    @Override
    public List<EventSummaryDto> viewEvents(String authorization) {
        LOGGER.debug("Iniciando consulta de eventos");
        var identity = loginService.authenticate(authorization);
        LOGGER.debug("Aplicando alcance de consulta: username={} role={} scope={}", identity.username(), identity.role(),
                "revisor".equals(identity.role()) ? "todos" : "propios");
        List<Event> events = "revisor".equals(identity.role())
                ? eventRepository.findAllByOrderByEventIdAsc()
                : eventRepository.findAllByReporter_UsernameOrderByEventIdAsc(identity.username());
        var summaries = events.stream().map(event -> new EventSummaryDto(
                event.getEventId(), event.getTitle(), event.getEventDate(), event.getEventArea(),
                event.getStatus(), event.getReporter().getUsername())).toList();
        LOGGER.info("Consulta de eventos completada: username={} role={} count={}",
                identity.username(), identity.role(), summaries.size());
        return summaries;
    }

    @Override
    public EventDetailDto viewEvent(int eventId, String authorization) {
        LOGGER.debug("Iniciando consulta de detalle: eventId={}", eventId);
        var identity = loginService.authenticate(authorization);
        if (eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador del evento debe ser positivo.");
        }
        // La consulta restringe el propietario antes de leer acciones o historial.
        Event event = ("revisor".equals(identity.role())
                ? eventRepository.findById(eventId)
                : eventRepository.findByEventIdAndReporter_Username(eventId, identity.username()))
                .orElseThrow(() -> {
                    LOGGER.debug("Evento no encontrado dentro del alcance del usuario: eventId={} username={} role={}",
                            eventId, identity.username(), identity.role());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "El evento no existe.");
                });

        var actions = correctiveActionRepository.findAllByEvent_EventIdOrderByActionIdAsc(eventId).stream()
                .map(action -> new CorrectiveActionDto(action.getActionId(), action.getDescription(),
                        action.getResponsible(), action.getTargetDate(), action.getStatus())).toList();
        var history = changeHistoryRepository.findAllByEvent_EventIdOrderByChangeDateAscChageHistoryIdAsc(eventId).stream()
                .map(change -> new ChangeHistoryDto(change.getChageHistoryId(), change.getChangeDate(),
                        change.getReporter().getUsername(), change.getChangeDescription())).toList();
        var detail = new EventDetailDto(event.getEventId(), event.getTitle(), event.getDescription(), event.getEventDate(),
                event.getEventArea(), event.getStatus(), event.getReporter().getUsername(), actions, history);
        LOGGER.info("Consulta de detalle completada: eventId={} username={} status={} actions={} history={}",
                eventId, identity.username(), event.getStatus(), actions.size(), history.size());
        return detail;
    }
}
