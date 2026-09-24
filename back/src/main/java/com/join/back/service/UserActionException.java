package com.join.back.service;

/**
 * A request the user cannot perform right now (limits, conflicts). The message is in Russian
 * and is shown to the user as is.
 */
public class UserActionException extends IllegalStateException {

    public UserActionException(String message) {
        super(message);
    }
}
