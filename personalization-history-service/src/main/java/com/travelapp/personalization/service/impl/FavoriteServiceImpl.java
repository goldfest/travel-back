package com.travelapp.personalization.service.impl;

import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.request.FavoriteRequest;
import com.travelapp.personalization.model.dto.response.FavoriteResponse;
import com.travelapp.personalization.model.entity.Favorite;
import com.travelapp.personalization.repository.FavoriteRepository;
import com.travelapp.personalization.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "favorites", key = "#userId"),
            @CacheEvict(value = "favoriteCount", key = "#userId")
    })
    public FavoriteResponse addToFavorites(Long userId, FavoriteRequest request) {
        log.info("Adding POI {} to favorites for user {}", request.getPoiId(), userId);

        // Проверяем, не добавлен ли уже в избранное
        if (favoriteRepository.existsByUserIdAndPoiId(userId, request.getPoiId())) {
            throw new IllegalStateException("POI already in favorites");
        }

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .poiId(request.getPoiId())
                .build();

        Favorite saved = favoriteRepository.save(favorite);
        log.debug("Favorite added with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "favorites", key = "#userId"),
            @CacheEvict(value = "favoriteCount", key = "#userId")
    })
    public void removeFromFavorites(Long userId, Long poiId) {
        log.info("Removing POI {} from favorites for user {}", poiId, userId);

        Favorite favorite = favoriteRepository.findByUserIdAndPoiId(userId, poiId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite not found"));

        favoriteRepository.delete(favorite);
        log.debug("Favorite removed successfully");
    }

    @Override
    @Cacheable(value = "favorites", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<FavoriteResponse> getUserFavorites(Long userId, Pageable pageable) {
        log.info("Fetching favorites for user {}", userId);

        return favoriteRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "favoriteCheck", key = "#userId + '-' + #poiId")
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long poiId) {
        log.debug("Checking if POI {} is favorite for user {}", poiId, userId);

        return favoriteRepository.existsByUserIdAndPoiId(userId, poiId);
    }

    @Override
    @Cacheable(value = "favoriteCount", key = "#userId")
    @Transactional(readOnly = true)
    public Long getFavoriteCount(Long userId) {
        log.debug("Getting favorite count for user {}", userId);

        return favoriteRepository.countByUserId(userId);
    }

    @Override
    @CacheEvict(value = {"favorites", "favoriteCount", "favoriteCheck"}, allEntries = true)
    public void deleteAllUserFavorites(Long userId) {
        log.info("Deleting all favorites for user {}", userId);

        favoriteRepository.deleteByUserId(userId);
    }

    private FavoriteResponse mapToResponse(Favorite favorite) {
        return FavoriteResponse.builder()
                .id(favorite.getId())
                .userId(favorite.getUserId())
                .poiId(favorite.getPoiId())
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}