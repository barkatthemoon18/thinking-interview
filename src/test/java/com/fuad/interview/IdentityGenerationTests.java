package com.fuad.interview;

import com.fuad.interview.models.ChangeHistory;
import com.fuad.interview.models.CorrectiveAction;
import com.fuad.interview.models.Event;
import com.fuad.interview.models.Roles;
import com.fuad.interview.models.Users;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:identity-generation-test")
@Transactional
class IdentityGenerationTests {

    @Autowired
    private EntityManager entityManager;

    @Test
    void generatesIdsFromOneAndContinuesAfterSeedData() {
        assertEquals(List.of(1, 2, 3), entityManager.createQuery(
                "select u.id from Users u order by u.id", Integer.class).getResultList());
        assertEquals(List.of(1, 2), entityManager.createQuery(
                "select r.id from Roles r order by r.id", Integer.class).getResultList());
        assertEquals(List.of(1, 2, 3, 4), entityManager.createQuery(
                "select e.eventId from Event e order by e.eventId", Integer.class).getResultList());
        assertEquals(List.of(1, 2), entityManager.createQuery(
                "select a.actionId from CorrectiveAction a order by a.actionId", Integer.class).getResultList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), entityManager.createQuery(
                "select h.chageHistoryId from ChangeHistory h order by h.chageHistoryId", Integer.class).getResultList());

        for (int offset = 0; offset < 2; offset++) {
            Users user = new Users();
            user.setUsername("usuario-prueba-" + offset);
            entityManager.persist(user);

            Roles role = new Roles();
            role.setRoleName("rol-prueba-" + offset);
            entityManager.persist(role);

            Event event = new Event();
            event.setTitle("Evento de prueba");
            event.setDescription("Descripcion de prueba");
            event.setEventDate(LocalDate.of(2026, 11, 1));
            event.setEventArea("Bodega");
            event.setStatus("reportado");
            event.setReporter(user);
            entityManager.persist(event);

            CorrectiveAction action = new CorrectiveAction();
            action.setDescription("Accion de prueba");
            action.setResponsible("Nombre Ficticio");
            action.setTargetDate(event.getEventDate().plusDays(1).atStartOfDay());
            action.setStatus("pendiente");
            action.setEvent(event);
            entityManager.persist(action);

            ChangeHistory history = new ChangeHistory();
            history.setChangeDate(event.getEventDate().atStartOfDay());
            history.setChangeDescription("Creacion de accion de prueba");
            history.setReporter(user);
            history.setEvent(event);
            entityManager.persist(history);

            entityManager.flush();
            entityManager.clear();

            int expectedOffset = offset;
            assertAll(
                    () -> assertEquals(4 + expectedOffset, user.getId()),
                    () -> assertEquals(3 + expectedOffset, role.getId()),
                    () -> assertEquals(5 + expectedOffset, event.getEventId()),
                    () -> assertEquals(3 + expectedOffset, action.getActionId()),
                    () -> assertEquals(7 + expectedOffset, history.getChageHistoryId()),
                    () -> assertEquals(user.getUsername(), entityManager.find(Users.class, user.getId()).getUsername()),
                    () -> assertEquals(role.getRoleName(), entityManager.find(Roles.class, role.getId()).getRoleName()),
                    () -> assertEquals(user.getId(), entityManager.find(Event.class, event.getEventId()).getReporter().getId()),
                    () -> assertEquals(event.getEventId(), entityManager.find(CorrectiveAction.class, action.getActionId()).getEvent().getEventId()),
                    () -> assertEquals(event.getEventId(), entityManager.find(ChangeHistory.class, history.getChageHistoryId()).getEvent().getEventId())
            );
        }
    }
}
