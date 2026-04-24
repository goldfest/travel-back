package com.travelapp.poi.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PoiMediaRejectRequest {

    @Size(max = 500, message = "Rejection reason must be less than 500 characters")
    private String reason;
}
