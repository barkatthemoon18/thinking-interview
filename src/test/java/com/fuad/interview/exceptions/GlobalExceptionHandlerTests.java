package com.fuad.interview.exceptions;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "not-found, 404", "empty-result, 404", "invalid, 400", "invalid-state, 409",
            "integrity, 409", "exists, 409", "optimistic-lock, 409", "unexpected, 500"
    })
    void mapsCrudExceptionsWithoutExposingInternalMessages(String kind, int expectedStatus) throws Exception {
        String path = "/test/errors/" + kind;
        mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(content().string(not(containsString("INTERNAL_SQL_DETAILS"))));
    }

    @Test
    void preservesExplicitHttpStatusesAndClientFacingReasons() throws Exception {
        mockMvc.perform(get("/test/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Acceso no permitido."));
    }

    @Test
    void includesValidationErrorsByField() throws Exception {
        mockMvc.perform(get("/test/errors/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.errors[0].message").value("El nombre es obligatorio."));
    }

    @Test
    void handlesMalformedJsonAsBadRequest() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void handlesInvalidParameterTypesAsBadRequest() throws Exception {
        mockMvc.perform(get("/test/items/invalid").param("filter", "all"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handlesMissingParametersAsBadRequest() throws Exception {
        mockMvc.perform(get("/test/items/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preservesMethodNotAllowedStatusAndAllowHeader() throws Exception {
        mockMvc.perform(post("/test/errors/invalid"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void preservesUnsupportedMediaTypeStatus() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void handlesUnknownRoutesAsNotFound() throws Exception {
        mockMvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/errors/{kind}")
        public String fail(@PathVariable String kind) throws NoSuchMethodException, MethodArgumentNotValidException {
            return switch (kind) {
                case "not-found" -> throw new EntityNotFoundException("INTERNAL_SQL_DETAILS");
                case "empty-result" -> throw new EmptyResultDataAccessException("INTERNAL_SQL_DETAILS", 1);
                case "invalid" -> throw new IllegalArgumentException("INTERNAL_SQL_DETAILS");
                case "invalid-state" -> throw new IllegalStateException("INTERNAL_SQL_DETAILS");
                case "integrity" -> throw new DataIntegrityViolationException("INTERNAL_SQL_DETAILS");
                case "exists" -> throw new EntityExistsException("INTERNAL_SQL_DETAILS");
                case "optimistic-lock" -> throw new OptimisticLockingFailureException("INTERNAL_SQL_DETAILS");
                case "unexpected" -> throw new RuntimeException("INTERNAL_SQL_DETAILS");
                case "forbidden" -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso no permitido.");
                case "validation" -> {
                    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Payload(""), "payload");
                    errors.addError(new FieldError("payload", "username", "El nombre es obligatorio."));
                    MethodParameter parameter = new MethodParameter(
                            TestController.class.getDeclaredMethod("body", Payload.class), 0);
                    throw new MethodArgumentNotValidException(parameter, errors);
                }
                default -> "ok";
            };
        }

        @PostMapping(value = "/test/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Payload body(@RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/test/items/{id}")
        public String item(@PathVariable int id, @RequestParam String filter) {
            return filter + id;
        }
    }

    record Payload(String username) {}
}
