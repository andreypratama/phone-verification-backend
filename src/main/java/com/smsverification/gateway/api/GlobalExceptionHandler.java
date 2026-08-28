package com.smsverification.gateway.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Request body is not valid JSON", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_PATH_PARAMETER",
                "Path parameter has an invalid format",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error while processing {}", request.getRequestURI(), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ApiError error = new ApiError(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(error);
    }
}
