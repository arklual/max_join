package com.join.back.web.controller;

import jakarta.persistence.EntityNotFoundException;
import com.join.back.service.UserActionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(UserActionException.class)
    public ResponseEntity<Map<String, String>> handleUserAction(UserActionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage(), "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("User is not authenticated")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", message, "code", "UNAUTHENTICATED"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "Photo is too large. Please upload an image up to 10 MB."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Operation violates database constraints."));
    }

    @ExceptionHandler(com.join.back.security.AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(com.join.back.security.AuthException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of(
                        "error", exception.getMessage(),
                        "code", exception.getCode()
                ));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            org.springframework.web.bind.MethodArgumentNotValidException exception) {
        org.springframework.validation.FieldError fieldError = exception.getBindingResult()
                .getFieldErrors().stream().findFirst().orElse(null);
        String code = "VALIDATION_FAILED";
        String message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Некорректные данные";
        if (fieldError != null) {
            String field = fieldError.getField();
            String violation = fieldError.getCode();
            if ("email".equals(field)) {
                code = "EMAIL_INVALID";
                message = "Некорректный email";
            } else if ("password".equals(field)
                    && ("Size".equals(violation) || "NotBlank".equals(violation))) {
                code = "PASSWORD_TOO_SHORT";
                message = "Пароль должен быть не короче 8 символов";
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message, "code", code));
    }
}
