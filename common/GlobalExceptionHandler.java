package com.group5.interview.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一转成 {@link ApiResponse}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return build(ec.getHttpStatus(), ec.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + ": " + fe.getDefaultMessage();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT.getCode(), msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadBody(Exception e) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.FILE_TOO_LARGE.getCode(), ErrorCode.FILE_TOO_LARGE.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.getCode(), "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL.getCode(), ErrorCode.INTERNAL.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
