package com.fuad.interview.service;

import com.fuad.interview.dto.ChangeEventStatusDto;
import com.fuad.interview.dto.CreateCorrectiveActionDto;
import com.fuad.interview.dto.EventDto;
import com.fuad.interview.models.ChangeHistory;
import com.fuad.interview.models.CorrectiveAction;
import com.fuad.interview.models.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventServiceTests extends ServiceTestSupport {
    private static final String REPORTER = token("reportante1", "reportante");
    private static final String REVIEWER = token("revisor1", "revisor");

    @ParameterizedTest
    @ValueSource(strings = {"reportante1", "reportante2"})
    void eventAndHistoryAlwaysBelongToTheTokenUser(String username) {
        eventService.addEvent(validEvent(), token(username, "reportante"));
        var eventCaptor = ArgumentCaptor.forClass(Event.class);
        var historyCaptor = ArgumentCaptor.forClass(ChangeHistory.class);
        verify(events).save(eventCaptor.capture());
        verify(history).save(historyCaptor.capture());
        assertEquals(username, eventCaptor.getValue().getReporter().getUsername());
        assertEquals(username, historyCaptor.getValue().getReporter().getUsername());
        assertEquals("reportado", eventCaptor.getValue().getStatus());
        assertEquals(LocalDateTime.now(CLOCK), historyCaptor.getValue().getChangeDate());
    }

    @Test
    void changingTheReporterRoleAfterCreationInvalidatesTheSameToken() {
        when(roles.findRoleNamesByUserId(1)).thenReturn(List.of("reportante"), List.of("revisor"));
        eventService.addEvent(validEvent(), REPORTER);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> eventService.addEvent(validEvent(), REPORTER));
        verify(events, times(1)).save(any());
        verify(history, times(1)).save(any());
    }

    @Test
    void reviewersCannotCreateEvents() {
        assertStatus(HttpStatus.FORBIDDEN, () -> eventService.addEvent(validEvent(), REVIEWER));
        verifyNoInteractions(events, actions, history);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void rejectsInvalidEventFieldsWithoutWritingAnything(EventDto dto) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> eventService.addEvent(dto, REPORTER));
        verifyNoInteractions(events, actions, history);
    }

    static Stream<EventDto> invalidEvents() {
        return Stream.of(null,
                new EventDto("1234", "Descripcion", EVENT_DATE, "Bodega"),
                new EventDto("x".repeat(121), "Descripcion", EVENT_DATE, "Bodega"),
                new EventDto("Titulo", " ", EVENT_DATE, "Bodega"),
                new EventDto("Titulo", "x".repeat(2001), EVENT_DATE, "Bodega"),
                new EventDto("Titulo", "Descripcion", null, "Bodega"),
                new EventDto("Titulo", "Descripcion", LocalDate.now(CLOCK).plusDays(1), "Bodega"),
                new EventDto("Titulo", "Descripcion", EVENT_DATE, "Otra"));
    }

    @Test
    void acceptsTodayUsingAnInjectableClock() {
        eventService.addEvent(new EventDto("Titulo", "Descripcion", LocalDate.now(CLOCK), "Bodega"), REPORTER);
        verify(events).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "reportado,en revisión,true", "en revisión,cerrado,true",
            "reportado,cerrado,false", "en revisión,reportado,false",
            "cerrado,en revisión,false", "reportado,reportado,false", "cerrado,cerrado,false"
    })
    void transitionsOnlyMoveOneStepForward(String previous, String next, boolean allowed) {
        Event event = event(1, previous, reporter1);
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        if (allowed) {
            eventService.changeEventStatus(1, new ChangeEventStatusDto(next), REVIEWER);
            assertEquals(next, event.getStatus());
            var captured = ArgumentCaptor.forClass(ChangeHistory.class);
            verify(history).save(captured.capture());
            assertEquals("Estado: " + previous + " -> " + next, captured.getValue().getChangeDescription());
            assertSame(reviewer, captured.getValue().getReporter());
        } else {
            assertStatus(HttpStatus.CONFLICT,
                    () -> eventService.changeEventStatus(1, new ChangeEventStatusDto(next), REVIEWER));
            assertEquals(previous, event.getStatus());
            verify(events, never()).save(any());
            verifyNoInteractions(history);
        }
    }

    @Test
    void closingRechecksPendingActionsWhenTheConditionChanges() {
        Event event = event(1, "en revisión", reporter1);
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        when(actions.existsByEvent_EventIdAndStatus(1, "pendiente")).thenReturn(true, false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> eventService.changeEventStatus(1, new ChangeEventStatusDto("cerrado"), REVIEWER));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("acciones pendientes"));
        assertEquals("en revisión", event.getStatus());
        verifyNoInteractions(history);
        eventService.changeEventStatus(1, new ChangeEventStatusDto("cerrado"), REVIEWER);
        assertEquals("cerrado", event.getStatus());
        verify(actions, times(2)).existsByEvent_EventIdAndStatus(1, "pendiente");
        verify(history, times(1)).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reportado", "cerrado"})
    void creatingAndCompletingActionsRequireAnEventInReview(String state) {
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event(1, state, reporter1)));
        assertStatus(HttpStatus.CONFLICT, () -> eventService.createCorrectiveAction(1, validAction(), REVIEWER));
        assertStatus(HttpStatus.CONFLICT, () -> eventService.completeCorrectiveAction(1, 7, REVIEWER));
        verifyNoInteractions(actions, history);
    }

    @ParameterizedTest
    @MethodSource("invalidActions")
    void actionFieldsAreValidatedBeforeWriting(CreateCorrectiveActionDto dto) {
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event(1, "en revisión", reporter1)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> eventService.createCorrectiveAction(1, dto, REVIEWER));
        verifyNoInteractions(actions, history);
    }

    static Stream<CreateCorrectiveActionDto> invalidActions() {
        return Stream.of(null,
                new CreateCorrectiveActionDto("1234", "Nombre", EVENT_DATE.atStartOfDay()),
                new CreateCorrectiveActionDto("x".repeat(501), "Nombre", EVENT_DATE.atStartOfDay()),
                new CreateCorrectiveActionDto("Descripcion", " ", EVENT_DATE.atStartOfDay()),
                new CreateCorrectiveActionDto("Descripcion", "Nombre", null),
                new CreateCorrectiveActionDto("Descripcion", "Nombre", EVENT_DATE.minusDays(1).atStartOfDay()));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 500})
    void actionDescriptionBoundsAndSameDayTargetAreAccepted(int length) {
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event(1, "en revisión", reporter1)));
        var response = eventService.createCorrectiveAction(1,
                new CreateCorrectiveActionDto("x".repeat(length), "Nombre", EVENT_DATE.atStartOfDay()), REVIEWER);
        assertEquals("pendiente", response.status());
        assertEquals(91, response.actionId());
        var captured = ArgumentCaptor.forClass(ChangeHistory.class);
        verify(history).save(captured.capture());
        assertTrue(captured.getValue().getChangeDescription().length() <= 500);
        assertSame(reviewer, captured.getValue().getReporter());
    }

    @Test
    void actionMustBelongToTheEventInTheUrl() {
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event(1, "en revisión", reporter1)));
        when(actions.findByActionIdAndEvent_EventId(7, 1)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> eventService.completeCorrectiveAction(1, 7, REVIEWER));
        verify(actions, never()).save(any());
        verifyNoInteractions(history);
    }

    @Test
    void completingAnActionTwiceDoesNotDuplicateTheHistory() {
        Event event = event(1, "en revisión", reporter1);
        CorrectiveAction action = action(7, event, "pendiente");
        when(events.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        when(actions.findByActionIdAndEvent_EventId(7, 1)).thenReturn(Optional.of(action));
        eventService.completeCorrectiveAction(1, 7, REVIEWER);
        assertEquals("completada", action.getStatus());
        assertStatus(HttpStatus.CONFLICT, () -> eventService.completeCorrectiveAction(1, 7, REVIEWER));
        verify(actions, times(1)).save(action);
        verify(history, times(1)).save(any());
    }

    private EventDto validEvent() {
        return new EventDto("Evento de prueba", "Descripcion", EVENT_DATE, "Bodega");
    }

    private CreateCorrectiveActionDto validAction() {
        return new CreateCorrectiveActionDto("Accion de prueba", "Nombre", EVENT_DATE.atStartOfDay());
    }
}
