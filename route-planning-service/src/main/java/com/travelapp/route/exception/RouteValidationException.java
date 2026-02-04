package com.travelapp.route.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RouteValidationException extends RuntimeException {

    public RouteValidationException(String message) {
        super(message);
    }

    public RouteValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}