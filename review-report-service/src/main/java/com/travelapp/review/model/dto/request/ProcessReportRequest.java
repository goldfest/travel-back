package com.travelapp.review.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcessReportRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String moderatorComment;
}