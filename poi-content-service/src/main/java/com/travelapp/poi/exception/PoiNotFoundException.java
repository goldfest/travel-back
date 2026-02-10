package com.travelapp.poi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PoiNotFoundException extends RuntimeException {

    public PoiNotFoundException(Long id) {
        super("POI not found with id: " + id);
    }

    public PoiNotFoundException(String message) {
        super(message);
    }
}