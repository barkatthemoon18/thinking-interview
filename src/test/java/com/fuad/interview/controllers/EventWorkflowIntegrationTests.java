package com.fuad.interview.controllers;

import com.fuad.interview.dto.LoginResponseDto;
import com.fuad.interview.dto.UserDto;
import com.fuad.interview.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:event-workflow-test")
@Sql("classpath:interview.sql")
class EventWorkflowIntegrationTests {
    @Autowired
    private LoginController loginController;
    @Autowired
    private InterviewController interviewController;
    @Autowired
    private GlobalExceptionHandler exceptionHandler;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Sin transacción de prueba: cada petición termina su propia transacción de servicio.
        mockMvc = MockMvcBuilders.standaloneSetup(loginController, interviewController)
                .setControllerAdvice(exceptionHandler).build();
    }

    @Test
    void completeWorkflowEnforcesClosingRulesAndRecordsEverySuccessfulChange() throws Exception {
        String reporter = loginAs("reportante1");
        String reviewer = loginAs("revisor1");
        mockMvc.perform(post("/api/create-event").header("Authorization", reporter)
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody()))
                .andExpect(status().isCreated());
        int eventId = jdbcTemplate.queryForObject("SELECT EVENT_ID FROM EVENT WHERE TITLE = ?",
                Integer.class, "Evento de flujo");
        mockMvc.perform(get("/api/events/{id}", eventId).header("Authorization", reporter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reporter").value("reportante1"))
                .andExpect(jsonPath("$.history.length()").value(1));

        changeState(eventId, "en revisión", reviewer, 204);
        String actionResponse = mockMvc.perform(post("/api/events/{id}/actions", eventId)
                        .header("Authorization", reviewer).contentType(MediaType.APPLICATION_JSON).content(actionBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pendiente"))
                .andReturn().getResponse().getContentAsString();
        int actionId = objectMapper.readTree(actionResponse).get("actionId").asInt();
        mockMvc.perform(patch("/api/events/{id}/status", eventId).header("Authorization", reviewer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"cerrado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("acciones pendientes")));
        assertEquals(3, historyCount(eventId));

        mockMvc.perform(patch("/api/events/{id}/actions/{action}/complete", eventId, actionId)
                        .header("Authorization", reviewer)).andExpect(status().isNoContent());
        changeState(eventId, "cerrado", reviewer, 204);
        mockMvc.perform(get("/api/events/{id}", eventId).header("Authorization", reporter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cerrado"))
                .andExpect(jsonPath("$.actions[0].status").value("completada"))
                .andExpect(jsonPath("$.history.length()").value(5))
                .andExpect(jsonPath("$.history[0].actor").value("reportante1"))
                .andExpect(jsonPath("$.history[4].actor").value("revisor1"))
                .andExpect(jsonPath("$.history[4].changeDate").isNotEmpty());
        changeState(eventId, "en revisión", reviewer, 409);
        assertEquals(5, historyCount(eventId));
    }

    @ParameterizedTest
    @CsvSource({"reportante1,1,2", "reportante2,2,1"})
    void changingTheUrlCannotExposeAnotherReportersDetails(String username, int ownId, int foreignId) throws Exception {
        String token = loginAs(username);
        mockMvc.perform(get("/api/events/{id}", ownId).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reporter").value(username));
        mockMvc.perform(get("/api/events/{id}", foreignId).header("Authorization", token)
                        .param("username", "revisor1").param("role", "revisor"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.actions").doesNotExist())
                .andExpect(jsonPath("$.history").doesNotExist());
        mockMvc.perform(get("/api/events/{id}", foreignId).header("Authorization", loginAs("revisor1")))
                .andExpect(status().isOk());
    }

    @Test
    void ownershipChangesInTheDatabaseImmediatelyAffectListsAndDetails() throws Exception {
        String first = loginAs("reportante1");
        String second = loginAs("reportante2");
        mockMvc.perform(get("/api/events/1").header("Authorization", first)).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE EVENT SET REPORTER_ID = ? WHERE EVENT_ID = ?", 2, 1);
        mockMvc.perform(get("/api/events/1").header("Authorization", first)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events").header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].eventId", containsInAnyOrder(3)));
        mockMvc.perform(get("/api/events").header("Authorization", second))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].eventId", containsInAnyOrder(1, 2, 4)));
        mockMvc.perform(get("/api/events/1").header("Authorization", second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reporter").value("reportante2"));
    }

    @Test
    void demotingAReviewerDoesNotLeaveAccessThroughAnOldTokenOrANewLogin() throws Exception {
        String oldToken = loginAs("revisor1");
        mockMvc.perform(get("/api/events").header("Authorization", oldToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/events/1").header("Authorization", oldToken)).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE USERS_ROLES SET ROLE_ID = ? WHERE USER_ID = ?", 1, 3);
        mockMvc.perform(get("/api/events").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events/1").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        changeState(1, "en revisión", oldToken, 401);

        String newToken = loginAs("revisor1");
        mockMvc.perform(get("/api/events").header("Authorization", newToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/events/1").header("Authorization", newToken)).andExpect(status().isNotFound());
        changeState(1, "en revisión", newToken, 403);
        assertEquals("reportado", jdbcTemplate.queryForObject("SELECT STATUS FROM EVENT WHERE EVENT_ID = 1", String.class));
    }

    @Test
    void removingAndRestoringTheDatabaseRoleChangesAuthorizationImmediately() throws Exception {
        String token = loginAs("reportante1");
        jdbcTemplate.update("DELETE FROM USERS_ROLES WHERE USER_ID = ?", 1);
        mockMvc.perform(get("/api/events").header("Authorization", token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/events/1").header("Authorization", token)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/create-event").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody())).andExpect(status().isForbidden());
        jdbcTemplate.update("INSERT INTO USERS_ROLES (USER_ID, ROLE_ID) VALUES (?, ?)", 1, 1);
        mockMvc.perform(get("/api/events/1").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void aDeletedUserCannotContinueUsingAnIssuedToken() throws Exception {
        jdbcTemplate.update("INSERT INTO USERS (USERNAME) VALUES (?)", "temporal");
        int userId = jdbcTemplate.queryForObject("SELECT USER_ID FROM USERS WHERE USERNAME = ?", Integer.class, "temporal");
        jdbcTemplate.update("INSERT INTO USERS_ROLES (USER_ID, ROLE_ID) VALUES (?, ?)", userId, 1);
        String token = loginAs("temporal");
        jdbcTemplate.update("DELETE FROM USERS_ROLES WHERE USER_ID = ?", userId);
        jdbcTemplate.update("DELETE FROM USERS WHERE USER_ID = ?", userId);
        mockMvc.perform(get("/api/events").header("Authorization", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events/1").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void anActionCannotBeCompletedThroughAnotherEventUrl() throws Exception {
        String reviewer = loginAs("revisor1");
        changeState(1, "en revisión", reviewer, 204);
        mockMvc.perform(patch("/api/events/1/actions/1/complete").header("Authorization", reviewer))
                .andExpect(status().isNotFound());
        assertEquals("pendiente", jdbcTemplate.queryForObject("SELECT STATUS FROM CORRECTIVE_ACTION WHERE ACTION_ID = 1",
                String.class));
        assertEquals(1, historyCount(1));
    }

    @Test
    void missingDatesAndInvalidIdsAreRejectedWithClearErrors() throws Exception {
        mockMvc.perform(post("/api/create-event").header("Authorization", loginAs("reportante1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titulo\",\"description\":\"Descripcion\",\"area\":\"Bodega\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("fecha")));
        String reviewer = loginAs("revisor1");
        mockMvc.perform(get("/api/events/0").header("Authorization", reviewer)).andExpect(status().isBadRequest());
        changeState(0, "en revisión", reviewer, 400);
        changeState(9999, "en revisión", reviewer, 404);
        changeState(1, "inventado", reviewer, 400);
    }

    @Test
    void aHistoryWriteFailureRollsBackTheEventCreation() throws Exception {
        jdbcTemplate.execute("ALTER TABLE CHANGE_HISTORY ADD CONSTRAINT TEST_BLOCK_CREATION_HISTORY "
                + "CHECK (CHANGE_DESCRIPTION <> 'Evento creado en estado: reportado')");
        try {
            mockMvc.perform(post("/api/create-event").header("Authorization", loginAs("reportante1"))
                            .contentType(MediaType.APPLICATION_JSON).content(eventBody()))
                    .andExpect(status().isConflict());
            assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT", Integer.class));
            assertEquals(6, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CHANGE_HISTORY", Integer.class));
        } finally {
            jdbcTemplate.execute("ALTER TABLE CHANGE_HISTORY DROP CONSTRAINT TEST_BLOCK_CREATION_HISTORY");
        }
    }

    private String loginAs(String username) throws Exception {
        String response = mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserDto(username))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponseDto.class).token();
    }

    private void changeState(int id, String state, String token, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/events/{id}/status", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", state))))
                .andExpect(status().is(expectedStatus));
    }

    private int historyCount(int eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CHANGE_HISTORY WHERE EVENT_ID = ?", Integer.class, eventId);
    }

    private String eventBody() {
        // Intento deliberado de asignar el evento a otro reportante: el DTO no expone ese campo.
        return """
                {"title":"Evento de flujo","description":"Descripcion ficticia","date":"2026-11-01",
                 "area":"Bodega","reporter":"reportante2"}
                """;
    }

    private String actionBody() {
        return """
                {"description":"Accion correctiva ficticia","responsible":"Nombre Ficticio",
                 "targetDate":"2026-11-01T00:00:00"}
                """;
    }
}
