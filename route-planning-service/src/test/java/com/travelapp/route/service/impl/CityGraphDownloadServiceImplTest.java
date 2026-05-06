package com.travelapp.route.service.impl;

import com.travelapp.route.client.GraphImportClient;
import com.travelapp.route.model.dto.internal.GraphImportTriggerRequest;
import com.travelapp.route.model.dto.response.CityGraphStatusResponse;
import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.repository.CityGraphVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CityGraphDownloadServiceImplTest {

    @Mock
    private GraphImportClient graphImportClient;

    @Mock
    private CityGraphVersionRepository cityGraphVersionRepository;

    @InjectMocks
    private CityGraphDownloadServiceImpl service;

    @Test
    void getStatus_shouldReturnReady_whenActiveVersionExists() {
        when(cityGraphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.of(version(CityGraphVersion.Status.ACTIVE)));

        CityGraphStatusResponse result = service.getStatus(10L);

        assertThat(result.cityId()).isEqualTo(10L);
        assertThat(result.ready()).isTrue();
        assertThat(result.downloading()).isFalse();
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void getStatus_shouldReturnDownloading_whenLatestVersionIsDraft() {
        when(cityGraphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(cityGraphVersionRepository.findFirstByCityIdOrderByVersionNoDesc(10L))
                .thenReturn(Optional.of(version(CityGraphVersion.Status.DRAFT)));

        CityGraphStatusResponse result = service.getStatus(10L);

        assertThat(result.ready()).isFalse();
        assertThat(result.downloading()).isTrue();
        assertThat(result.status()).isEqualTo("DOWNLOADING");
    }

    @Test
    void getStatus_shouldReturnFailed_whenLatestVersionIsFailed() {
        when(cityGraphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(cityGraphVersionRepository.findFirstByCityIdOrderByVersionNoDesc(10L))
                .thenReturn(Optional.of(version(CityGraphVersion.Status.FAILED)));

        CityGraphStatusResponse result = service.getStatus(10L);

        assertThat(result.ready()).isFalse();
        assertThat(result.downloading()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void download_shouldTriggerGraphImport_whenGraphIsNotDownloaded() {
        when(cityGraphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(cityGraphVersionRepository.findFirstByCityIdOrderByVersionNoDesc(10L))
                .thenReturn(Optional.empty());
        when(graphImportClient.importCityGraph(any(GraphImportTriggerRequest.class))).thenReturn(Map.of("status", "started"));

        CityGraphStatusResponse result = service.download(10L);

        assertThat(result.downloading()).isTrue();
        assertThat(result.status()).isEqualTo("DOWNLOADING");
        ArgumentCaptor<GraphImportTriggerRequest> captor = ArgumentCaptor.forClass(GraphImportTriggerRequest.class);
        verify(graphImportClient).importCityGraph(captor.capture());
        assertThat(captor.getValue().getCityId()).isEqualTo(10L);
    }

    @Test
    void download_shouldReturnCurrentStatus_withoutTriggeringImport_whenGraphIsReady() {
        when(cityGraphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.of(version(CityGraphVersion.Status.ACTIVE)));

        CityGraphStatusResponse result = service.download(10L);

        assertThat(result.status()).isEqualTo("READY");
        verifyNoInteractions(graphImportClient);
    }

    @Test
    void download_shouldRemoveInFlightFlagAndRethrow_whenGraphImportClientFails() {
        when(cityGraphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(cityGraphVersionRepository.findFirstByCityIdOrderByVersionNoDesc(10L))
                .thenReturn(Optional.empty());
        when(graphImportClient.importCityGraph(any(GraphImportTriggerRequest.class)))
                .thenThrow(new RuntimeException("graph importer unavailable"));

        assertThatThrownBy(() -> service.download(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("graph importer unavailable");

        assertThat(service.getStatus(10L).status()).isEqualTo("NOT_DOWNLOADED");
    }

    private CityGraphVersion version(CityGraphVersion.Status status) {
        CityGraphVersion version = new CityGraphVersion();
        version.setId(1L);
        version.setCityId(10L);
        version.setVersionNo(1);
        version.setStatus(status);
        return version;
    }
}
