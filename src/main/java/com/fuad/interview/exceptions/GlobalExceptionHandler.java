package com.fuad.interview.exceptions;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({EntityNotFoundException.class, EmptyResultDataAccessException.class})
    public ResponseEntity<Object> handleNotFound(Exception exception, WebRequest request) {
        return error(HttpStatus.NOT_FOUND, "El recurso solicitado no existe.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleInvalidArgument(IllegalArgumentException exception, WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Los datos proporcionados no son válidos.", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleInvalidState(IllegalStateException exception, WebRequest request) {
        return error(HttpStatus.CONFLICT, "La operación no está permitida en el estado actual del recurso.", request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, EntityExistsException.class})
    public ResponseEntity<Object> handleDataConflict(Exception exception, WebRequest request) {
        return error(HttpStatus.CONFLICT,
                "La operación genera datos duplicados o incumple una restricción de integridad.", request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Object> handleConcurrentUpdate(Exception exception, WebRequest request) {
        return error(HttpStatus.CONFLICT,
                "El recurso fue modificado por otra operación. Vuelve a intentarlo.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        LOGGER.error("Error no controlado al procesar una solicitud", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno al procesar la solicitud.", request);
    }

    // Spring conserva aquí los estados y encabezados de errores HTTP como 400, 405 y 415.
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = defaultDetail(status);
        if (!status.is5xxServerError() && exception instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null) {
            detail = responseStatusException.getReason();
        }

        ProblemDetail problem = problem(status, detail, request);
        if (exception instanceof MethodArgumentNotValidException validationException) {
            problem.setProperty("errors", validationException.getBindingResult().getAllErrors().stream()
                    .map(error -> Map.of(
                            "field", error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                            "message", Objects.requireNonNullElse(error.getDefaultMessage(), "Valor no válido.")))
                    .toList());
        }

        if (status.is5xxServerError()) {
            LOGGER.error("Error al procesar una solicitud HTTP", exception);
        }
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    private ResponseEntity<Object> error(HttpStatus status, String detail, WebRequest request) {
        return ResponseEntity.status(status).body(problem(status, detail, request));
    }

    private ProblemDetail problem(HttpStatusCode status, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
        return problem;
    }

    private String defaultDetail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "La solicitud contiene datos inválidos o faltan campos obligatorios.";
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "El método HTTP no está permitido para este recurso.";
            case 406 -> "El formato de respuesta solicitado no está disponible.";
            case 409 -> "La operación entra en conflicto con el estado actual del recurso.";
            case 413 -> "El contenido de la solicitud supera el tamaño permitido.";
            case 415 -> "El tipo de contenido de la solicitud no está soportado.";
            case 503 -> "El servicio no está disponible temporalmente.";
            default -> status.is5xxServerError()
                    ? "Ocurrió un error interno al procesar la solicitud."
                    : "No fue posible procesar la solicitud.";
        };
    }
}
