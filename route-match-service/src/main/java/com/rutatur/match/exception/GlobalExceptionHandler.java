package com.rutatur.match.exception;

import com.rutatur.common.dto.ApiError;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
