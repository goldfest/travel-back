package com.travelapp.city.security;

import lombok.Value;

@Value
public class AuthPrincipal {
    Long id;
    String role;
    String status;
    Boolean isBlocked;
    Long homeCityId;
}