package com.travelapp.poi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PoiTypeNotFoundException extends RuntimeException {

    public PoiTypeNotFoundException(Long id) {
        super("POI Type not found with id: " + id);
    }

    public PoiTypeNotFoundException(String message) {
        super(message);
    }
}