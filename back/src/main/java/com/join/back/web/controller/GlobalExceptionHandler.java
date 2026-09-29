package com.join.back.web.controller;

import jakarta.persistence.EntityNotFoundException;
import com.join.back.model.dto.ErrorResponse;
import com.join.back.service.UserActionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(UserActionException.class)
    public ResponseEntity<ErrorResponse> handleUserAction(UserActionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("User is not authenticated")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.withCode(message, "UNAUTHENTICATED"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("Photo is too large. Please upload an image up to 10 MB."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("Operation violates database constraints."));
    }

    @ExceptionHandler(com.join.back.security.AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(com.join.back.security.AuthException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.withCode(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
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
                .body(ErrorResponse.withCode(message, code));
    }
}
