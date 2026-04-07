package com.travelapp.route.model.dto;

import lombok.Data;

@Data
public class InternalUserResponse {
    private Long id;
    private String email;
    private String username;
    private String role;
    private String status;
    private Boolean isBlocked;
    private String avatarUrl;
    private Long homeCityId;
}