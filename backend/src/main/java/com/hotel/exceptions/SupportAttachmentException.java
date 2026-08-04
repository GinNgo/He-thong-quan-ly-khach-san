package com.hotel.exceptions;

import org.springframework.http.HttpStatus;

public class SupportAttachmentException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public SupportAttachmentException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
