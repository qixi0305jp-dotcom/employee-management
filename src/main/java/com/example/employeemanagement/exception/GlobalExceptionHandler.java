package com.example.employeemanagement.exception;

import com.example.employeemanagement.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(
            BusinessException e) {

        Result<Void> result =
                Result.error(400, e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<Void>> handleResourceNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, e.getMessage()));
    }

    // Validation 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {

        Map<String, String> errors = new HashMap<>();

        e.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity.badRequest().body(Result.error(
                400,
                "参数校验失败",
                errors
        ));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<Result<Void>> handleInvalidRequest(Exception e) {
        return ResponseEntity.badRequest().body(Result.error(400, "请求格式或参数错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(e.getHeaders()).body(Result.error(405, "不支持的请求方法"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(e.getHeaders()).body(Result.error(415, "不支持的请求内容类型"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException e) {
        // Explicit JSON allows the error contract even when Accept excludes JSON.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error(406, "不支持的响应内容类型"));
    }

    // 其它系统异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(
            Exception e) {

        Result<Void> result =
                Result.error(500, "系统异常");

        log.error("系统发生未处理异常", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(result);
    }

    // 已认证用户的访问拒绝。
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(
            AccessDeniedException e) {

        Result<Void> result =
                Result.error(403, "没有权限");

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(result);
    }


    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e) {

        log.warn("上传文件超过大小限制");

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "上传文件或请求超过大小限制"));
    }


}
