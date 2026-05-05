package com.travelapp.personalization.service;

import com.travelapp.personalization.client.PoiClient;
import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.external.PoiDto;
import com.travelapp.personalization.model.dto.request.FavoriteRequest;
import com.travelapp.personalization.model.dto.response.FavoriteResponse;
import com.travelapp.personalization.model.entity.Favorite;
import com.travelapp.personalization.repository.FavoriteRepository;
import com.travelapp.personalization.service.impl.FavoriteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock
    private PoiClient poiClient;

    @Mock
    private FavoriteRepository favoriteRepository;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    @Test
    void addToFavorites_shouldSaveFavorite_whenPoiExistsAndNotFavoriteYet() {
        Long userId = 10L;
        Long poiId = 100L;
        FavoriteRequest request = new FavoriteRequest(poiId);
        Favorite saved = favorite(1L, userId, poiId);

        when(poiClient.getPoiById(poiId)).thenReturn(poi(poiId));
        when(favoriteRepository.existsByUserIdAndPoiId(userId, poiId)).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class))).thenReturn(saved);

        FavoriteResponse result = favoriteService.addToFavorites(userId, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPoiId()).isEqualTo(poiId);

        verify(poiClient).getPoiById(poiId);
        verify(favoriteRepository).save(argThat(favorite ->
                favorite.getUserId().equals(userId) && favorite.getPoiId().equals(poiId)
        ));
    }

    @Test
    void addToFavorites_shouldThrowResourceNotFoundException_whenPoiDoesNotExist() {
        Long userId = 10L;
        Long poiId = 404L;
        FavoriteRequest request = new FavoriteRequest(poiId);

        when(poiClient.getPoiById(poiId)).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> favoriteService.addToFavorites(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("POI not found");

        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    void addToFavorites_shouldThrowIllegalStateException_whenPoiAlreadyInFavorites() {
        Long userId = 10L;
        Long poiId = 100L;
        FavoriteRequest request = new FavoriteRequest(poiId);

        when(poiClient.getPoiById(poiId)).thenReturn(poi(poiId));
        when(favoriteRepository.existsByUserIdAndPoiId(userId, poiId)).thenReturn(true);

        assertThatThrownBy(() -> favoriteService.addToFavorites(userId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in favorites");

        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    void removeFromFavorites_shouldDeleteFavorite_whenFavoriteExists() {
        Long userId = 10L;
        Long poiId = 100L;
        Favorite favorite = favorite(1L, userId, poiId);

        when(favoriteRepository.findByUserIdAndPoiId(userId, poiId)).thenReturn(Optional.of(favorite));

        favoriteService.removeFromFavorites(userId, poiId);

        verify(favoriteRepository).delete(favorite);
    }

    @Test
    void removeFromFavorites_shouldThrowResourceNotFoundException_whenFavoriteDoesNotExist() {
        Long userId = 10L;
        Long poiId = 100L;

        when(favoriteRepository.findByUserIdAndPoiId(userId, poiId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.removeFromFavorites(userId, poiId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Favorite not found");

        verify(favoriteRepository, never()).delete(any(Favorite.class));
    }

    @Test
    void getUserFavorites_shouldMapFavoritesToResponses() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);
        Favorite favorite = favorite(1L, userId, 100L);
        Page<Favorite> page = new PageImpl<>(List.of(favorite), pageable, 1);

        when(favoriteRepository.findByUserId(userId, pageable)).thenReturn(page);

        Page<FavoriteResponse> result = favoriteService.getUserFavorites(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPoiId()).isEqualTo(100L);
    }

    @Test
    void isFavorite_shouldReturnRepositoryResult() {
        when(favoriteRepository.existsByUserIdAndPoiId(10L, 100L)).thenReturn(true);

        boolean result = favoriteService.isFavorite(10L, 100L);

        assertThat(result).isTrue();
    }

    @Test
    void getFavoriteCount_shouldReturnRepositoryCount() {
        when(favoriteRepository.countByUserId(10L)).thenReturn(3L);

        Long result = favoriteService.getFavoriteCount(10L);

        assertThat(result).isEqualTo(3L);
    }

    @Test
    void deleteAllUserFavorites_shouldDeleteByUserId() {
        favoriteService.deleteAllUserFavorites(10L);

        verify(favoriteRepository).deleteByUserId(10L);
    }

    private Favorite favorite(Long id, Long userId, Long poiId) {
        return Favorite.builder()
                .id(id)
                .userId(userId)
                .poiId(poiId)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
    }

    private PoiDto poi(Long id) {
        PoiDto poi = new PoiDto();
        poi.setId(id);
        poi.setName("Музей");
        poi.setSlug("museum");
        return poi;
    }
}
