package com.travelapp.route.client;

import com.travelapp.route.model.dto.internal.GraphImportTriggerRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "graph-importer",
        url = "${services.graph-import.url:http://graph-importer:8080}"
)
public interface GraphImportClient {

    @PostMapping("/internal/graph-import")
    Map<String, Object> importCityGraph(@RequestBody GraphImportTriggerRequest request);
}
