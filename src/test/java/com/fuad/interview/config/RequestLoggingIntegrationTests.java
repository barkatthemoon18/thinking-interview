package com.fuad.interview.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:request-logging-test")
class RequestLoggingIntegrationTests {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RequestLoggingFilter filter;

    private MockMvc mockMvc;
    private Logger applicationLogger;
    private ListAppender<ILoggingEvent> appender;
    private Map<String, String> previousContext;

    @BeforeEach
    void captureLogs() {
        // Las dependencias de prueba incluyen filtros de Spring Security que la API no usa.
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        previousContext = MDC.getCopyOfContextMap();
        MDC.clear();
        applicationLogger = (Logger) LoggerFactory.getLogger("com.fuad.interview");
        appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        applicationLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        applicationLogger.detachAppender(appender);
        appender.stop();
        if (previousContext == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }

    @Test
    void correlatesHttpControllerAndServiceLogsWithoutLoggingCredentialsOrBodies() throws Exception {
        var response = mockMvc.perform(post("/api/login")
                        .header("Authorization", "sensitive-header")
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "untrusted-client-id")
                        .queryParam("probe", "sensitive-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reportante1\",\"probe\":\"sensitive-body\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertEquals(requestId, UUID.fromString(requestId).toString());
        assertNotEquals("untrusted-client-id", requestId);
        assertCorrelated(requestId);
        assertTrue(appender.list.stream().anyMatch(event -> event.getLoggerName().endsWith("LoginController")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getLoggerName().endsWith("LoginServiceImpl")));
        assertTrue(messages().contains("HTTP iniciado: method=POST path=/api/login"));
        assertTrue(messages().matches("(?s).*HTTP finalizado: method=POST path=/api/login status=200 durationMs=\\d+.*"));
        String token = objectMapper.readTree(response.getContentAsString()).get("token").asText();
        for (String sensitive : new String[]{token, "sensitive-header", "sensitive-query", "sensitive-body"}) {
            assertFalse(messages().contains(sensitive));
        }
        assertNull(MDC.get("requestId"));
    }

    @ParameterizedTest
    @CsvSource({"malformed,400", "unauthorized,401", "forbidden,403", "missing,404",
            "method,405", "pending,409", "media,415"})
    void correlatesRejectionsIncludingFailuresBeforeTheController(String kind, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = switch (kind) {
            case "malformed" -> post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{invalid");
            case "unauthorized" -> get("/api/events");
            case "forbidden" -> patch("/api/events/1/status").header("Authorization", token("reportante1", "reportante"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"en revisión\"}");
            case "missing" -> get("/api/events/9999").header("Authorization", token("revisor1", "revisor"));
            case "method" -> get("/api/login");
            case "pending" -> patch("/api/events/2/status").header("Authorization", token("revisor1", "revisor"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"cerrado\"}");
            case "media" -> post("/api/login").contentType(MediaType.TEXT_PLAIN).content("sensitive-body");
            default -> throw new IllegalArgumentException(kind);
        };
        var response = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse();
        assertCorrelated(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
        assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().contains("status=" + expectedStatus)));
        assertTrue(messages().contains("HTTP finalizado:"));
        assertTrue(messages().contains("status=" + expectedStatus + " durationMs="));
        assertFalse(messages().contains("Solicitud completada"));
        assertFalse(messages().contains("guardada en transacción"));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void usesDifferentIdsForSuccessiveRequestsAndRestoresThePreviousContext() throws Exception {
        MDC.put("requestId", "outer-context");
        MDC.put("otherContext", "preserved");
        var first = mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized()).andReturn().getResponse();
        String firstId = first.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertCorrelated(firstId);
        assertEquals("outer-context", MDC.get("requestId"));
        appender.list.clear();
        var second = mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized()).andReturn().getResponse();
        String secondId = second.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertCorrelated(secondId);
        assertNotEquals(firstId, secondId);
        assertEquals("outer-context", MDC.get("requestId"));
        assertEquals("preserved", MDC.get("otherContext"));
    }

    @Test
    void logsUnhandledFailuresWithTheirStackTraceAndCleansTheContext() {
        var request = new MockHttpServletRequest("GET", "/api/events");
        var response = new MockHttpServletResponse();
        var failure = new ServletException("Uncontrolled failure");
        var thrown = assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (incoming, outgoing) -> { throw failure; }));
        assertSame(failure, thrown);
        assertCorrelated(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
        assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                && event.getThrowableProxy() != null && event.getFormattedMessage().contains("status=500")));
        assertFalse(messages().contains("HTTP finalizado"));
        assertNull(MDC.get("requestId"));
    }

    private void assertCorrelated(String requestId) {
        assertNotNull(requestId);
        assertFalse(appender.list.isEmpty());
        assertTrue(appender.list.stream().allMatch(event -> requestId.equals(event.getMDCPropertyMap().get("requestId"))));
    }

    private String messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (all, message) -> all + "\n" + message);
    }

    private String token(String username, String role) {
        String payload = "{\"scheme\":\"login\",\"username\":\"" + username + "\",\"role\":\"" + role + "\"}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
