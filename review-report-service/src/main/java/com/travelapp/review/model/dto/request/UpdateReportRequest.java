package com.travelapp.review.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateReportRequest {

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;

    @Size(max = 500, message = "Photo URL must not exceed 500 characters")
    private String photoUrl;
}