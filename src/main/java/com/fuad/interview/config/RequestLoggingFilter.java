package com.fuad.interview.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousRequestId = MDC.get(REQUEST_ID);
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        MDC.put(REQUEST_ID, requestId);
        try {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            LOGGER.info("HTTP iniciado: method={} path={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            LOGGER.info("HTTP finalizado: method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMillis(startedAt));
        } catch (IOException | ServletException | RuntimeException exception) {
            LOGGER.error("HTTP fallido: method={} path={} status=500 durationMs={}",
                    request.getMethod(), request.getRequestURI(), elapsedMillis(startedAt), exception);
            throw exception;
        } finally {
            // Los hilos del servidor se reutilizan: la siguiente petición no debe heredar este ID.
            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID);
            } else {
                MDC.put(REQUEST_ID, previousRequestId);
            }
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
