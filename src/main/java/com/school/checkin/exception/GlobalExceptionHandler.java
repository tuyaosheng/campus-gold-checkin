package com.school.checkin.exception;

import com.school.checkin.common.ApiResponse;
import com.school.checkin.common.ErrorCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex, HttpServletResponse response) {
        response.setStatus(ex.getCode());
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleBadRequest(Exception ex, HttpServletResponse response) {
        response.setStatus(ErrorCode.BAD_REQUEST);
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, "请求参数不合法");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex, HttpServletResponse response) {
        response.setStatus(ErrorCode.INTERNAL_ERROR);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, ex.getMessage());
    }
}
