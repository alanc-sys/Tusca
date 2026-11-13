package com.rutatur.poi.exception;

import com.rutatur.common.dto.ApiError;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RutaturException.class)
    public ResponseEntity<ApiError> handleRutaturException(RutaturException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), ex.getDetails());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(error);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }
}
