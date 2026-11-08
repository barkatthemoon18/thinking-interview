package com.fuad.interview.service;

import com.fuad.interview.dto.ChangeEventStatusDto;
import com.fuad.interview.dto.CreateCorrectiveActionDto;
import com.fuad.interview.dto.EventDto;
import com.fuad.interview.models.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class UsersServiceTests extends ServiceTestSupport {
    private static final String REPORTER = token("reportante1", "reportante");
    private static final String REVIEWER = token("revisor1", "revisor");

    @Test
    void reporterQueriesAreAlwaysRestrictedToTheTokenUsername() {
        Event own = event(1, "reportado", reporter1);
        when(events.findAllByReporter_UsernameOrderByEventIdAsc("reportante1")).thenReturn(List.of(own));
        when(events.findByEventIdAndReporter_Username(1, "reportante1")).thenReturn(Optional.of(own));
        assertEquals("reportante1", usersService.viewEvents(REPORTER).get(0).reporter());
        assertEquals("reportante1", usersService.viewEvent(1, REPORTER).reporter());
        verify(events, never()).findAllByOrderByEventIdAsc();
        verify(events, never()).findById(anyInt());
    }

    @Test
    void foreignDetailsNeverReadActionsOrHistory() {
        when(events.findByEventIdAndReporter_Username(2, "reportante1")).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> usersService.viewEvent(2, REPORTER));
        verifyNoInteractions(actions, history);
        verify(events, never()).findById(anyInt());
    }

    @Test
    void aChangedOwnerIsObservedOnTheNextListAndDetailRequest() {
        Event own = event(1, "reportado", reporter1);
        when(events.findAllByReporter_UsernameOrderByEventIdAsc("reportante1"))
                .thenReturn(List.of(own), List.of());
        when(events.findByEventIdAndReporter_Username(1, "reportante1"))
                .thenReturn(Optional.of(own), Optional.empty());
        assertEquals(1, usersService.viewEvents(REPORTER).size());
        assertEquals(1, usersService.viewEvent(1, REPORTER).eventId());
        assertEquals(0, usersService.viewEvents(REPORTER).size());
        assertStatus(HttpStatus.NOT_FOUND, () -> usersService.viewEvent(1, REPORTER));
        verify(actions, times(1)).findAllByEvent_EventIdOrderByActionIdAsc(1);
        verify(history, times(1)).findAllByEvent_EventIdOrderByChangeDateAscChageHistoryIdAsc(1);
    }

    @Test
    void reviewerAccessIsRevokedWhenTheDatabaseRoleChanges() {
        Event foreign = event(1, "reportado", reporter1);
        when(events.findAllByOrderByEventIdAsc()).thenReturn(List.of(foreign));
        when(events.findById(1)).thenReturn(Optional.of(foreign));
        assertEquals(1, usersService.viewEvents(REVIEWER).size());
        assertEquals(1, usersService.viewEvent(1, REVIEWER).eventId());

        when(roles.findRoleNamesByUserId(3)).thenReturn(List.of("reportante"));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> usersService.viewEvents(REVIEWER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> usersService.viewEvent(1, REVIEWER));
        String demotedToken = token("revisor1", "reportante");
        assertEquals(0, usersService.viewEvents(demotedToken).size());
        assertStatus(HttpStatus.NOT_FOUND, () -> usersService.viewEvent(1, demotedToken));
        verify(events, times(1)).findAllByOrderByEventIdAsc();
        verify(events, times(1)).findById(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "detail", "create", "state", "action", "complete"})
    void everyServiceRejectsARevokedDatabaseRoleBeforeAccessingEvents(String operation) {
        when(roles.findRoleNamesByUserId(3)).thenReturn(List.of());
        assertStatus(HttpStatus.FORBIDDEN, () -> request(operation, REVIEWER));
        verifyNoInteractions(events, actions, history);
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "detail", "create", "state", "action", "complete"})
    void everyServiceRejectsADeletedUserBeforeAccessingEvents(String operation) {
        when(users.findByUsername("revisor1")).thenReturn(null);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> request(operation, REVIEWER));
        verifyNoInteractions(events, actions, history);
    }

    @ParameterizedTest
    @ValueSource(strings = {"state", "action", "complete"})
    void reporterTokensCannotInvokeReviewerServicesDirectly(String operation) {
        assertStatus(HttpStatus.FORBIDDEN, () -> request(operation, REPORTER));
        verifyNoInteractions(events, actions, history);
    }

    @Test
    void changingOnlyTheTokenRoleCannotGrantReviewerAccess() {
        String forgedToken = token("reportante1", "revisor");
        assertStatus(HttpStatus.UNAUTHORIZED, () -> usersService.viewEvents(forgedToken));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> usersService.viewEvent(2, forgedToken));
        verifyNoInteractions(events, actions, history);
    }

    private void request(String operation, String authorization) {
        switch (operation) {
            case "list" -> usersService.viewEvents(authorization);
            case "detail" -> usersService.viewEvent(1, authorization);
            case "create" -> eventService.addEvent(
                    new EventDto("Titulo", "Descripcion", EVENT_DATE, "Bodega"), authorization);
            case "state" -> eventService.changeEventStatus(1, new ChangeEventStatusDto("en revisión"), authorization);
            case "action" -> eventService.createCorrectiveAction(1,
                    new CreateCorrectiveActionDto("Descripcion", "Nombre", EVENT_DATE.atStartOfDay()), authorization);
            case "complete" -> eventService.completeCorrectiveAction(1, 7, authorization);
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
