package com.smsverification.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsverification.gateway.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void unauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String code,
            String message
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsetsHolder.UTF_8_NAME);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError apiError = new ApiError(
                Instant.now(clock),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                Map.of()
        );
        objectMapper.writeValue(response.getOutputStream(), apiError);
    }

    private static final class StandardCharsetsHolder {
        private static final String UTF_8_NAME = "UTF-8";

        private StandardCharsetsHolder() {
        }
    }
}
