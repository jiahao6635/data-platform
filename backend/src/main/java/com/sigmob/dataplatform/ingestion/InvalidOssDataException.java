package com.sigmob.dataplatform.ingestion;

public class InvalidOssDataException extends RuntimeException {

    public InvalidOssDataException(String message) {
        super(message);
    }

    public InvalidOssDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

