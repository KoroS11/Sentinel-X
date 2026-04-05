package com.sentinelx.auth.exception;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException(String message) {
        super(message);
    }
}
