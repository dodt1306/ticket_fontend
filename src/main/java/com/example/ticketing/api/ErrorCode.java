package com.example.ticketing.api;

public enum ErrorCode {

    SEAT_ALREADY_HELD(409, "One or more selected seats are no longer available"),
    HOLD_EXPIRED(410, "Hold has expired"),
    INVALID_REQUEST(400, "Invalid request"),
    LIMIT_EXCEEDED(403, "Seat limit exceeded"),
    NO_ADJACENT_SEATS(403, "No Adjacent Seats"),
    FORBIDDEN(403, "Hold Not Found or Forbidden"),
    ALREADY_DONE(403, "Already Done"),
    NOT_READY(403, "Not Ready"),
    INTERNAL_ERROR(500, "Internal server error");
    public final int httpStatus;
    public final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
