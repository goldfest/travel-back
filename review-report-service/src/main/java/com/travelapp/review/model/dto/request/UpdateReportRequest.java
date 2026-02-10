package com.travelapp.review.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportRequest {

    private String status;

    @Size(max = 1000, message = "Comment must be less than 1000 characters")
    private String moderatorComment;

    private String photoUrl;
}