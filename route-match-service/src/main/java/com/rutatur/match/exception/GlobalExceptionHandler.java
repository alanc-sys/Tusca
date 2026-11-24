package com.rutatur.match.exception;

import com.rutatur.common.dto.ApiError;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RutaturException.class)
    public ResponseEntity<ApiError> handleRutaturException(RutaturException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), ex.getDetails());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, "Parámetros inválidos", request.getRequestURI(), details);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable root = ex.getMostSpecificCause();
        if (root instanceof RutaturException rutaturEx) {
            return handleRutaturException(rutaturEx, request);
        }
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, "Cuerpo JSON inválido", request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleTypeMismatchOrMissingParam(Exception ex, HttpServletRequest request) {
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, "Parámetro inválido o faltante", request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de contenido no soportado: " + ex.getContentType(),
                request.getRequestURI());
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus()).body(error);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCode.METHOD_NOT_ALLOWED,
                "Método HTTP no soportado: " + ex.getMethod(),
                request.getRequestURI());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus()).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String causeMessage = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), causeMessage);
        if (causeMessage != null && causeMessage.contains("uk_match_rating_rater")) {
            ApiError conflict = ApiError.of(
                    ErrorCode.RATING_ALREADY_EXISTS,
                    "Este match ya fue calificado por el usuario",
                    request.getRequestURI());
            return ResponseEntity.status(ErrorCode.RATING_ALREADY_EXISTS.getHttpStatus()).body(conflict);
        }
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR,
                "Los datos enviados no cumplen las restricciones de integridad",
                request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiError error = ApiError.of(
                ErrorCode.INTERNAL_ERROR,
                "Error interno del servidor",
                request.getRequestURI());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(error);
    }
}
