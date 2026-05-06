package com.travelapp.route.service.impl;

import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.repository.CityGraphVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphVersionServiceImplTest {

    @Mock
    private CityGraphVersionRepository repository;

    @InjectMocks
    private GraphVersionServiceImpl service;

    @Test
    void getRequiredActiveVersionId_shouldReturnActiveVersionId() {
        CityGraphVersion version = version(55L, CityGraphVersion.Status.ACTIVE);
        when(repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.of(version));

        Long result = service.getRequiredActiveVersionId(10L);

        assertThat(result).isEqualTo(55L);
    }

    @Test
    void getActiveVersionOrThrow_shouldReturnActiveVersion() {
        CityGraphVersion version = version(55L, CityGraphVersion.Status.ACTIVE);
        when(repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.of(version));

        CityGraphVersion result = service.getActiveVersionOrThrow(10L);

        assertThat(result).isEqualTo(version);
    }

    @Test
    void getActiveVersionOrThrow_shouldThrowResourceNotFoundException_whenActiveGraphMissing() {
        when(repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveVersionOrThrow(10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("не подготовлен граф дорог");
    }

    @Test
    void hasActiveVersion_shouldReturnTrueOnlyWhenActiveVersionExists() {
        when(repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(10L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.of(version(55L, CityGraphVersion.Status.ACTIVE)));
        when(repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(20L, CityGraphVersion.Status.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service.hasActiveVersion(10L)).isTrue();
        assertThat(service.hasActiveVersion(20L)).isFalse();
    }

    private CityGraphVersion version(Long id, CityGraphVersion.Status status) {
        CityGraphVersion version = new CityGraphVersion();
        version.setId(id);
        version.setCityId(10L);
        version.setVersionNo(3);
        version.setStatus(status);
        return version;
    }
}
