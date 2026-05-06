package com.travelapp.graphimport.service.impl;

import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.service.GraphImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphImportAsyncServiceTest {

    @Mock
    private GraphImportService graphImportService;

    @InjectMocks
    private GraphImportAsyncService service;

    @Test
    void importCityGraphAsync_shouldDelegateToGraphImportService() {
        GraphImportRequest request = request(10L);

        service.importCityGraphAsync(request);

        verify(graphImportService).importCityGraph(request);
    }

    @Test
    void importCityGraphAsync_shouldSuppressException() {
        GraphImportRequest request = request(10L);
        doThrow(new IllegalStateException("OSM parse error")).when(graphImportService).importCityGraph(request);

        service.importCityGraphAsync(request);

        verify(graphImportService).importCityGraph(request);
    }

    private GraphImportRequest request(Long cityId) {
        GraphImportRequest request = new GraphImportRequest();
        request.setCityId(cityId);
        return request;
    }
}
