package com.travelapp.graphimport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.graphimport.client.InternalPoiClient;
import com.travelapp.graphimport.client.RouteCacheClient;
import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.model.dto.response.GraphImportStatsResponse;
import com.travelapp.graphimport.model.entity.CityGraphVersion;
import com.travelapp.graphimport.repository.CityGraphVersionRepository;
import com.travelapp.graphimport.repository.PoiGraphBindingRepository;
import com.travelapp.graphimport.repository.RoadEdgeRepository;
import com.travelapp.graphimport.repository.RoadNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphImportServiceImplTest {

    @Mock
    private CityGraphVersionRepository graphVersionRepository;

    @Mock
    private RoadNodeRepository roadNodeRepository;

    @Mock
    private RoadEdgeRepository roadEdgeRepository;

    @Mock
    private PoiGraphBindingRepository poiGraphBindingRepository;

    @Mock
    private InternalPoiClient poiClient;

    @Mock
    private RouteCacheClient routeCacheClient;

    @Mock
    private GraphImportPersistenceService persistenceService;

    @InjectMocks
    private GraphImportServiceImpl service;

    @Test
    void getCityImportStats_shouldReturnActiveVersionStats() {
        CityGraphVersion version = version(55L, 10L, 3, CityGraphVersion.Status.ACTIVE);
        version.setImportedAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        when(graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.of(version));
        when(roadNodeRepository.countByGraphVersion_Id(55L)).thenReturn(100L);
        when(roadEdgeRepository.countByGraphVersion_Id(55L)).thenReturn(250L);
        when(poiGraphBindingRepository.countByGraphVersion_Id(55L)).thenReturn(15L);

        GraphImportStatsResponse result = service.getCityImportStats(10L);

        assertThat(result.getCityId()).isEqualTo(10L);
        assertThat(result.getActiveGraphVersionId()).isEqualTo(55L);
        assertThat(result.getActiveGraphVersionNo()).isEqualTo(3);
        assertThat(result.getActiveGraphStatus()).isEqualTo("ACTIVE");
        assertThat(result.getNodeCount()).isEqualTo(100L);
        assertThat(result.getEdgeCount()).isEqualTo(250L);
        assertThat(result.getBindingCount()).isEqualTo(15L);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void getCityImportStats_shouldReturnFailedVersion_whenActiveMissingAndFailedExists() {
        CityGraphVersion failed = version(56L, 10L, 4, CityGraphVersion.Status.FAILED);
        failed.setFailureReason("OSM-файл не найден");
        when(graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.FAILED))
                .thenReturn(Optional.of(failed));

        GraphImportStatsResponse result = service.getCityImportStats(10L);

        assertThat(result.getActiveGraphVersionId()).isEqualTo(56L);
        assertThat(result.getActiveGraphVersionNo()).isEqualTo(4);
        assertThat(result.getActiveGraphStatus()).isEqualTo("FAILED");
        assertThat(result.getNodeCount()).isZero();
        assertThat(result.getEdgeCount()).isZero();
        assertThat(result.getBindingCount()).isZero();
        assertThat(result.getFailureReason()).isEqualTo("OSM-файл не найден");
    }

    @Test
    void getCityImportStats_shouldReturnMissing_whenNoGraphVersionsExist() {
        when(graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.FAILED))
                .thenReturn(Optional.empty());

        GraphImportStatsResponse result = service.getCityImportStats(10L);

        assertThat(result.getActiveGraphVersionId()).isNull();
        assertThat(result.getActiveGraphStatus()).isEqualTo("MISSING");
        assertThat(result.getNodeCount()).isZero();
        assertThat(result.getEdgeCount()).isZero();
        assertThat(result.getBindingCount()).isZero();
    }

    @Test
    void importCityGraph_shouldRejectPathOutsideAllowedRoot() throws Exception {
        Path root = Files.createTempDirectory("osm-root");
        Path outside = Files.createTempFile("outside", ".osm");
        ReflectionTestUtils.setField(service, "allowedOsmRoot", root.toString());
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        GraphImportRequest request = new GraphImportRequest();
        request.setCityId(10L);
        request.setOsmFilePath(outside.toString());

        assertThatThrownBy(() -> service.importCityGraph(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Некорректный путь к OSM-файлу");

        verifyNoInteractions(poiClient, persistenceService, routeCacheClient);
    }

    @Test
    void importCityGraph_shouldRejectMissingFileAndNotCreateVersion() throws Exception {
        Path root = Files.createTempDirectory("osm-root");
        ReflectionTestUtils.setField(service, "allowedOsmRoot", root.toString());
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        GraphImportRequest request = new GraphImportRequest();
        request.setCityId(10L);
        request.setOsmFilePath("missing.osm");

        assertThatThrownBy(() -> service.importCityGraph(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Некорректный путь к OSM-файлу");

        verify(graphVersionRepository, never()).save(any(CityGraphVersion.class));
        verifyNoInteractions(poiClient, persistenceService, routeCacheClient);
    }

    private CityGraphVersion version(Long id, Long cityId, Integer versionNo, CityGraphVersion.Status status) {
        CityGraphVersion version = new CityGraphVersion();
        version.setId(id);
        version.setCityId(cityId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        return version;
    }
}
