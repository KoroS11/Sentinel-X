package com.sentinelx.user.exception;

public class UserOperationNotAllowedException extends RuntimeException {

    public UserOperationNotAllowedException(String message) {
        super(message);
    }
}