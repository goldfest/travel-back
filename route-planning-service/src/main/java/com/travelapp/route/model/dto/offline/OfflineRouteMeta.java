package com.travelapp.route.model.dto.offline;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class OfflineRouteMeta {
    private Long userId;
    private Long routeId;
    private String routeName;
    private Long cityId;

    private boolean includesPoiDetails;
    private boolean includesMapData;

    private long sizeBytes;
    private Instant downloadedAt;

    private String format;   // travelapp-offline-v1
    private String version;  // 1.0
}