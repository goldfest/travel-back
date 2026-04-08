package com.travelapp.route.model.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphImportTriggerRequest {
    private Long cityId;
    private String osmFilePath;
}
