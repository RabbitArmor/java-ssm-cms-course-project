package edu.course.lab.cms.platform.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<?>> handleBusiness(ApiException exception) {
        return failure(exception.status(), exception.code(), exception.getMessage(), null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindingValidation(BindException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? new FieldViolation(fieldError.getField(), fieldError.isBindingFailure()
                                ? "参数格式无效" : fieldError.getDefaultMessage())
                        : new FieldViolation(error.getObjectName(), error.getDefaultMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "参数校验失败", violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintValidation(ConstraintViolationException exception) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "参数校验失败", violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String field = exception.getName() != null ? exception.getName() : "parameter";
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "参数校验失败",
                List.of(new FieldViolation(field, "参数格式无效")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "请求体格式无效", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleOther(Exception exception) {
        if (exception instanceof ErrorResponse error) {
            HttpStatusCode status = error.getStatusCode();
            if (exception instanceof HandlerMethodValidationException validation && status.is4xxClientError()) {
                List<FieldViolation> violations = validation.getParameterValidationResults().stream()
                        .flatMap(result -> {
                            String field = resolveParameterName(result.getMethodParameter());
                            return result.getResolvableErrors().stream()
                                    .map(err -> err instanceof FieldError fieldError
                                            ? new FieldViolation(fieldError.getField(), fieldError.isBindingFailure()
                                                    ? "参数格式无效" : fieldError.getDefaultMessage())
                                            : new FieldViolation(field, err.getDefaultMessage()));
                        })
                        .toList();
                return failure(status, "VALIDATION_FAILED", "参数校验失败", violations.isEmpty() ? null : violations);
            }
            if (status.is4xxClientError()) {
                return ResponseEntity.status(status).headers(error.getHeaders())
                        .body(ApiResponse.failure("HTTP_" + status.value(), "请求无效", null));
            }
        }
        LOGGER.error("Unexpected request failure", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误", null);
    }

    private static String resolveParameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String explicit = firstNonBlank(requestParam.name(), requestParam.value());
            if (explicit != null) {
                return explicit;
            }
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String explicit = firstNonBlank(pathVariable.name(), pathVariable.value());
            if (explicit != null) {
                return explicit;
            }
        }
        String parameterName = parameter.getParameterName();
        if (parameterName != null && !parameterName.isBlank()) {
            return parameterName;
        }
        return "arg" + parameter.getParameterIndex();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private ResponseEntity<ApiResponse<?>> failure(HttpStatusCode status, String code, String message, Object data) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, data));
    }

    record FieldViolation(String field, String message) {
    }
}
