package com.corebanking.api.config;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Requête invalide");
        pd.setType(URI.create("https://api.core-banking/errors/bad-request"));
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflit");
        pd.setType(URI.create("https://api.core-banking/errors/conflict"));
        return pd;
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockException ex) {
        log.warn("Optimistic lock: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Conflit de concurrence — veuillez réessayer la transaction");
        pd.setTitle("Conflit de version");
        pd.setType(URI.create("https://api.core-banking/errors/optimistic-lock"));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        pd.setTitle("Erreur de validation");
        pd.setType(URI.create("https://api.core-banking/errors/validation"));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInternal(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue");
        pd.setTitle("Erreur interne");
        pd.setType(URI.create("https://api.core-banking/errors/internal"));
        return pd;
    }
}
