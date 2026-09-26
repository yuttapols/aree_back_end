package com.roti5dao.common.web;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.security.PasswordPolicy;
import com.roti5dao.common.web.ApiResponse.FieldErrorItem;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * แปลง exception ทุกชนิดเป็น {@link ApiResponse} รูปแบบเดียวกัน
 * และไม่ส่ง stack trace / ข้อความภายในของระบบออกไปให้ client
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return build(ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(PasswordPolicy.PolicyViolation.class)
    ResponseEntity<ApiResponse<Void>> handlePasswordPolicy(PasswordPolicy.PolicyViolation ex) {
        return build(ex.getCode(), ex.getMessage(), List.of(ex.field()));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        List<FieldErrorItem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorItem> fields = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldErrorItem(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorItem> fields = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.defaultMessage(), List.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage(), List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), List.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        return build(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CONCURRENT_UPDATE.defaultMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String constraint = constraintName(ex);
        ErrorCode code;
        if (constraint.contains("uq_app_user_phone")) {
            code = ErrorCode.PHONE_ALREADY_USED;
        } else if (constraint.contains("uq_app_user_email")) {
            code = ErrorCode.EMAIL_ALREADY_USED;
        } else if (constraint.startsWith("uq_")) {
            code = ErrorCode.DUPLICATE_VALUE;
        } else {
            log.warn("Data integrity violation: {}", constraint.isEmpty() ? ex.getMostSpecificCause().getClass().getSimpleName() : constraint);
            code = ErrorCode.INVALID_OPERATION;
        }
        return build(code, code.defaultMessage(), List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();
        ErrorCode code = anonymous ? ErrorCode.UNAUTHORIZED : ErrorCode.FORBIDDEN;
        return build(code, code.defaultMessage(), List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of());
    }

    private static ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, List<FieldErrorItem> fields) {
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(code, message, fields));
    }

    private static String constraintName(DataIntegrityViolationException ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName().toLowerCase(Locale.ROOT);
            }
            t = t.getCause();
        }
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null) {
            var m = java.util.regex.Pattern.compile("constraint \"([a-z0-9_]+)\"").matcher(msg);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }
}
