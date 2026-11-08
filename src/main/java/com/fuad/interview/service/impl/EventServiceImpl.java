package com.fuad.interview.service.impl;

import com.fuad.interview.dto.ChangeEventStatusDto;
import com.fuad.interview.dto.CorrectiveActionDto;
import com.fuad.interview.dto.CreateCorrectiveActionDto;
import com.fuad.interview.dto.EventDto;
import com.fuad.interview.models.ChangeHistory;
import com.fuad.interview.models.CorrectiveAction;
import com.fuad.interview.models.Event;
import com.fuad.interview.models.Users;
import com.fuad.interview.repositories.ChangeHistoryRepository;
import com.fuad.interview.repositories.CorrectiveActionRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@Transactional
@AllArgsConstructor
public class EventServiceImpl implements EventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);

    private static final String REPORTED = "reportado";
    private static final String IN_REVIEW = "en revisión";
    private static final String CLOSED = "cerrado";
    private static final String PENDING = "pendiente";
    private static final String COMPLETED = "completada";
    private static final Set<String> AREAS = Set.of("Planta Norte", "Planta Sur", "Bodega", "Administración");
    private static final Set<String> STATES = Set.of(REPORTED, IN_REVIEW, CLOSED);
    private final EventRepository eventRepository;
    private final UsersRepository usersRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final CorrectiveActionRepository correctiveActionRepository;
    private final LoginService loginService;
    private final Clock clock;

    @Override
    public ResponseEntity<String> addEvent(EventDto eventDto, String authorization) {
        LOGGER.debug("Iniciando creación de evento");
        Users actor = requireActor(authorization, "reportante");
        if (eventDto == null) {
            throw invalid("Los datos del evento son obligatorios.");
        }
        requireText(eventDto.title(), 5, 120, "El título");
        requireText(eventDto.description(), 1, 2000, "La descripción");
        if (eventDto.date() == null || eventDto.date().isAfter(LocalDate.now(clock))) {
            throw invalid("La fecha del evento es obligatoria y no puede ser futura.");
        }
        if (eventDto.area() == null || !AREAS.contains(eventDto.area())) {
            throw invalid("El área debe ser Planta Norte, Planta Sur, Bodega o Administración.");
        }

        Event event = new Event();
        event.setTitle(eventDto.title());
        event.setDescription(eventDto.description());
        event.setEventDate(eventDto.date());
        event.setEventArea(eventDto.area());
        event.setStatus(REPORTED);
        event.setReporter(actor);
        eventRepository.save(event);
        recordChange(event, actor, "Evento creado en estado: " + REPORTED);
        LOGGER.info("Creación de evento guardada en transacción: eventId={} actor={} status={}",
                event.getEventId(), actor.getUsername(), event.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body("Event created successfully");
    }

    @Override
    public void changeEventStatus(int eventId, ChangeEventStatusDto statusDto, String authorization) {
        LOGGER.debug("Iniciando cambio de estado: eventId={}", eventId);
        Users actor = requireActor(authorization, "revisor");
        if (statusDto == null || statusDto.status() == null || !STATES.contains(statusDto.status())) {
            throw invalid("El estado debe ser reportado, en revisión o cerrado.");
        }
        Event event = lockEvent(eventId);
        String previous = event.getStatus();
        String next = statusDto.status();
        LOGGER.debug("Validando transición: eventId={} actor={} previousStatus={} nextStatus={}",
                eventId, actor.getUsername(), previous, next);
        if (!(REPORTED.equals(previous) && IN_REVIEW.equals(next))
                && !(IN_REVIEW.equals(previous) && CLOSED.equals(next))) {
            throw conflict("Solo se permite avanzar de reportado a en revisión y de en revisión a cerrado.");
        }
        if (CLOSED.equals(next) && correctiveActionRepository.existsByEvent_EventIdAndStatus(eventId, PENDING)) {
            throw conflict("No se puede cerrar el evento mientras tenga acciones pendientes.");
        }
        event.setStatus(next);
        eventRepository.save(event);
        recordChange(event, actor, "Estado: " + previous + " -> " + next);
        LOGGER.info("Cambio de estado guardado en transacción: eventId={} actor={} previousStatus={} nextStatus={}",
                eventId, actor.getUsername(), previous, next);
    }

    @Override
    public CorrectiveActionDto createCorrectiveAction(int eventId, CreateCorrectiveActionDto actionDto,
                                                      String authorization) {
        LOGGER.debug("Iniciando creación de acción correctiva: eventId={}", eventId);
        Users actor = requireActor(authorization, "revisor");
        Event event = lockEvent(eventId);
        requireInReview(event);
        if (actionDto == null) {
            throw invalid("Los datos de la acción son obligatorios.");
        }
        requireText(actionDto.description(), 5, 500, "La descripción de la acción");
        requireText(actionDto.responsible(), 1, 30, "El responsable");
        if (actionDto.targetDate() == null || actionDto.targetDate().toLocalDate().isBefore(event.getEventDate())) {
            throw invalid("La fecha objetivo es obligatoria y no puede ser anterior a la fecha del evento.");
        }

        CorrectiveAction action = new CorrectiveAction();
        action.setDescription(actionDto.description());
        action.setResponsible(actionDto.responsible());
        action.setTargetDate(actionDto.targetDate());
        action.setStatus(PENDING);
        action.setEvent(event);
        correctiveActionRepository.save(action);
        recordChange(event, actor, "Acción creada: #" + action.getActionId() + " (estado pendiente)");
        LOGGER.info("Creación de acción guardada en transacción: eventId={} actionId={} actor={} status={}",
                eventId, action.getActionId(), actor.getUsername(), action.getStatus());
        return new CorrectiveActionDto(action.getActionId(), action.getDescription(), action.getResponsible(),
                action.getTargetDate(), action.getStatus());
    }

    @Override
    public void completeCorrectiveAction(int eventId, int actionId, String authorization) {
        LOGGER.debug("Iniciando finalización de acción correctiva: eventId={} actionId={}", eventId, actionId);
        Users actor = requireActor(authorization, "revisor");
        Event event = lockEvent(eventId);
        requireInReview(event);
        requirePositiveId(actionId);
        CorrectiveAction action = correctiveActionRepository.findByActionIdAndEvent_EventId(actionId, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La acción no existe en este evento."));
        if (!PENDING.equals(action.getStatus())) {
            LOGGER.debug("Acción rechazada por su estado: eventId={} actionId={} status={}",
                    eventId, actionId, action.getStatus());
            throw conflict("Solo se puede completar una acción pendiente.");
        }
        action.setStatus(COMPLETED);
        correctiveActionRepository.save(action);
        recordChange(event, actor, "Acción #" + actionId + ": pendiente -> completada");
        LOGGER.info("Finalización de acción guardada en transacción: eventId={} actionId={} actor={} status={}",
                eventId, actionId, actor.getUsername(), COMPLETED);
    }

    private Users requireActor(String authorization, String role) {
        var identity = loginService.requireRole(authorization, role);
        Users actor = usersRepository.findByUsername(identity.username());
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no existe.");
        }
        LOGGER.debug("Actor autorizado: actor={} role={}", actor.getUsername(), identity.role());
        return actor;
    }

    private Event lockEvent(int eventId) {
        requirePositiveId(eventId);
        // Todas las escrituras del evento comparten este bloqueo, incluido el cierre.
        LOGGER.debug("Solicitando bloqueo de escritura: eventId={}", eventId);
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El evento no existe."));
        LOGGER.debug("Bloqueo de escritura obtenido: eventId={} status={}", eventId, event.getStatus());
        return event;
    }

    private void requireInReview(Event event) {
        if (!IN_REVIEW.equals(event.getStatus())) {
            LOGGER.debug("Operación de acción rechazada por estado del evento: eventId={} status={}",
                    event.getEventId(), event.getStatus());
            throw conflict("Las acciones solo pueden crearse o completarse mientras el evento está en revisión.");
        }
    }

    private void recordChange(Event event, Users actor, String description) {
        ChangeHistory history = new ChangeHistory();
        history.setChangeDate(LocalDateTime.now(clock));
        history.setChangeDescription(description);
        history.setEvent(event);
        history.setReporter(actor);
        changeHistoryRepository.save(history);
        LOGGER.debug("Historial guardado en transacción: eventId={} actor={} change={}",
                event.getEventId(), actor.getUsername(), description);
    }

    private void requireText(String value, int min, int max, String field) {
        if (value == null || value.isBlank() || value.length() < min || value.length() > max) {
            throw invalid(field + " debe tener entre " + min + " y " + max + " caracteres y no puede estar vacío.");
        }
    }

    private void requirePositiveId(int id) {
        if (id <= 0) {
            throw invalid("Los identificadores deben ser positivos.");
        }
    }

    private ResponseStatusException invalid(String message) {
        LOGGER.debug("Validación rechazada: reason={}", message);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        LOGGER.debug("Regla de negocio rechazada: reason={}", message);
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
