package com.travelapp.auth.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
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