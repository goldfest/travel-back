package com.travelapp.auth.model.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String username;
    private String phone;
    private String avatarUrl;
    private Long homeCityId;
    private String preferencesJson;
}