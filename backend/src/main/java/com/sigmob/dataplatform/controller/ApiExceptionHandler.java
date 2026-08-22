package com.sigmob.dataplatform.controller;

import java.io.IOException;
import java.time.OffsetDateTime;

import com.sigmob.dataplatform.dto.ApiModels;
import com.sigmob.dataplatform.ingestion.InvalidOssDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidOssDataException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiModels.ErrorResponse> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(new ApiModels.ErrorResponse(
                "INVALID_REQUEST",
                exception.getMessage(),
                OffsetDateTime.now()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiModels.ErrorResponse> ioError(IOException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiModels.ErrorResponse(
                "FILE_READ_ERROR",
                exception.getMessage(),
                OffsetDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiModels.ErrorResponse> serverError(Exception exception) {
        log.error("未处理的 API 异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiModels.ErrorResponse(
                "INTERNAL_ERROR",
                "服务暂时无法处理请求",
                OffsetDateTime.now()));
    }
}

