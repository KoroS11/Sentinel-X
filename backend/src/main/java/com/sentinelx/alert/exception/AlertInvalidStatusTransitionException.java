package com.sentinelx.alert.exception;

public class AlertInvalidStatusTransitionException extends RuntimeException {

    public AlertInvalidStatusTransitionException(String message) {
        super(message);
    }
}