package com.travelapp.personalization.service;

import com.travelapp.personalization.model.dto.request.FavoriteRequest;
import com.travelapp.personalization.model.dto.response.FavoriteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FavoriteService {

    FavoriteResponse addToFavorites(Long userId, FavoriteRequest request);

    void removeFromFavorites(Long userId, Long poiId);

    Page<FavoriteResponse> getUserFavorites(Long userId, Pageable pageable);

    boolean isFavorite(Long userId, Long poiId);

    Long getFavoriteCount(Long userId);

    void deleteAllUserFavorites(Long userId);
}