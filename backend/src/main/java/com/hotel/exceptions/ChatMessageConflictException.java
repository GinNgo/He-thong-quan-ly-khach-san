package com.hotel.exceptions;

public class ChatMessageConflictException extends RuntimeException {
    public static final String ERROR_CODE = "CLIENT_MESSAGE_ID_REUSED";

    public ChatMessageConflictException() {
        super("The client message id was reused with different message content.");
    }
}
