package com.fuad.interview.controllers;

import com.fuad.interview.dto.LoginResponseDto;
import com.fuad.interview.dto.UserDto;
import com.fuad.interview.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:login-test")
@Transactional
class LoginIntegrationTests {
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
        mockMvc = MockMvcBuilders.standaloneSetup(loginController, interviewController)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @ParameterizedTest
    @CsvSource({"reportante1,reportante", "reportante2,reportante", "revisor1,revisor"})
    void loginEncodesTheRequestedPayloadWithTheDatabaseRole(String username, String role) throws Exception {
        var payload = objectMapper.readTree(Base64.getDecoder().decode(loginAs(username)));
        assertEquals(3, payload.size());
        assertEquals("login", payload.get("scheme").asText());
        assertEquals(username, payload.get("username").asText());
        assertEquals(role, payload.get("role").asText());
    }

    @Test
    void loginRejectsUnknownUsersAndMissingUsernames() throws Exception {
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing\"}"))
                .andExpect(status().isUnauthorized());
        for (String body : new String[]{"{}", "{\"username\":\" \"}", "{\"username\":null}"}) {
            mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void roleComesFromTheDatabaseEvenWhenTheLoginBodyRequestsAnotherRole() throws Exception {
        String response = mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reportante1\",\"role\":\"revisor\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(response, LoginResponseDto.class).token();
        var payload = objectMapper.readTree(Base64.getDecoder().decode(token));
        assertEquals("reportante", payload.get("role").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer "})
    void reportantesOnlyListTheirOwnEventsUsingRawOrBearerTokens(String prefix) throws Exception {
        mockMvc.perform(get("/api/events").header("Authorization", prefix + loginAs("reportante1"))
                        .param("username", "reportante2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventId", containsInAnyOrder(1, 3)))
                .andExpect(jsonPath("$[*].reporter", everyItem(is("reportante1"))));
    }

    @Test
    void reviewersListAllEvents() throws Exception {
        mockMvc.perform(get("/api/events").header("Authorization", loginAs("revisor1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventId", containsInAnyOrder(1, 2, 3, 4)));
    }

    @Test
    void requestsWithoutAuthorizationAreRejected() throws Exception {
        mockMvc.perform(get("/api/events").param("username", "reportante1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/create-event").contentType(MediaType.APPLICATION_JSON).content(eventBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/events/1/actions/1/complete")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "not-json",
            "{\"scheme\":\"other\",\"username\":\"reportante1\",\"role\":\"reportante\"}",
            "{\"scheme\":\"login\",\"username\":\"missing\",\"role\":\"reportante\"}",
            "{\"scheme\":\"login\",\"username\":\"reportante1\",\"role\":\"revisor\"}"
    })
    void rejectsInvalidPayloadsAndRolesThatDoNotMatchTheDatabase(String payload) throws Exception {
        mockMvc.perform(get("/api/events").header("Authorization", encode(payload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedBase64() throws Exception {
        mockMvc.perform(get("/api/events").header("Authorization", "invalid!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRolesAreCheckedAgainOnEveryRequest() throws Exception {
        String token = loginAs("reportante1");
        jdbcTemplate.update("UPDATE USERS_ROLES SET ROLE_ID = ? WHERE USER_ID = ?", 2, 1);
        mockMvc.perform(get("/api/events").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events").header("Authorization", loginAs("reportante1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void usersWithoutASingleSupportedRoleCannotLogin() throws Exception {
        jdbcTemplate.update("DELETE FROM USERS_ROLES WHERE USER_ID = ?", 1);
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reportante1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewersCannotCreateEvents() throws Exception {
        mockMvc.perform(post("/api/create-event").header("Authorization", loginAs("revisor1"))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody()))
                .andExpect(status().isForbidden());
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM EVENT", Integer.class));
    }

    @Test
    void eventAndHistoryUseTheUserFromAuthorizationRegardlessOfTheBodyReporter() throws Exception {
        mockMvc.perform(post("/api/create-event").header("Authorization", loginAs("reportante1"))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody()))
                .andExpect(status().isCreated());
        Integer eventId = jdbcTemplate.queryForObject("SELECT EVENT_ID FROM EVENT WHERE TITLE = ?",
                Integer.class, "Evento de login");
        assertEquals(1, jdbcTemplate.queryForObject("SELECT REPORTER_ID FROM EVENT WHERE EVENT_ID = ?",
                Integer.class, eventId));
        assertEquals("reportado", jdbcTemplate.queryForObject("SELECT STATUS FROM EVENT WHERE EVENT_ID = ?",
                String.class, eventId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT USER_ID FROM CHANGE_HISTORY WHERE EVENT_ID = ?",
                Integer.class, eventId));
    }

    @Test
    void reviewOperationsRequireTheDatabaseReviewerRole() throws Exception {
        String reporterToken = loginAs("reportante1");
        mockMvc.perform(patch("/api/events/1/status").header("Authorization", reporterToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"en revisión\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/events/1/actions").header("Authorization", reporterToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"Accion de prueba","responsible":"Nombre Ficticio",
                         "targetDate":"2026-11-15T00:00:00"}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/events/1/actions/1/complete").header("Authorization", reporterToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/events/1/status").header("Authorization", loginAs("revisor1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"en revisión\"}"))
                .andExpect(status().isNoContent());
    }

    private String loginAs(String username) throws Exception {
        String response = mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserDto(username))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponseDto.class).token();
    }

    private String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String eventBody() {
        return """
                {"title":"Evento de login","description":"Descripcion de prueba",
                 "date":"2026-11-01","area":"Bodega","reporter":"reportante2"}
                """;
    }
}
