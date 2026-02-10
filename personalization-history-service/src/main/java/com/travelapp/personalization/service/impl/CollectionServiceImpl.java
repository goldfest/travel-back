package com.travelapp.personalization.service.impl;

import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.request.CollectionPoiRequest;
import com.travelapp.personalization.model.dto.request.CollectionRequest;
import com.travelapp.personalization.model.dto.response.CollectionResponse;
import com.travelapp.personalization.model.entity.Collection;
import com.travelapp.personalization.model.entity.CollectionPoi;
import com.travelapp.personalization.repository.CollectionPoiRepository;
import com.travelapp.personalization.repository.CollectionRepository;
import com.travelapp.personalization.service.CollectionService;
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
public class CollectionServiceImpl implements CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionPoiRepository collectionPoiRepository;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "collections", key = "#userId"),
            @CacheEvict(value = "collectionSearch", key = "#userId")
    })
    public CollectionResponse createCollection(Long userId, CollectionRequest request) {
        log.info("Creating collection '{}' for user {}", request.getName(), userId);

        // Проверяем уникальность названия для пользователя
        if (collectionRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new IllegalStateException("Collection with this name already exists");
        }

        Collection collection = Collection.builder()
                .name(request.getName())
                .description(request.getDescription())
                .coverUrl(request.getCoverUrl())
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Collection saved = collectionRepository.save(collection);
        log.debug("Collection created with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "collections", key = "#userId"),
            @CacheEvict(value = "collection", key = "#collectionId"),
            @CacheEvict(value = "collectionSearch", key = "#userId")
    })
    public CollectionResponse updateCollection(Long userId, Long collectionId, CollectionRequest request) {
        log.info("Updating collection {} for user {}", collectionId, userId);

        Collection collection = getCollectionEntity(userId, collectionId);

        // Проверяем уникальность нового названия (если оно изменилось)
        if (!collection.getName().equals(request.getName()) &&
                collectionRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new IllegalStateException("Collection with this name already exists");
        }

        collection.setName(request.getName());
        collection.setDescription(request.getDescription());
        collection.setCoverUrl(request.getCoverUrl());
        collection.setUpdatedAt(LocalDateTime.now());

        Collection updated = collectionRepository.save(collection);

        return mapToResponse(updated);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "collections", key = "#userId"),
            @CacheEvict(value = "collection", key = "#collectionId"),
            @CacheEvict(value = "collectionSearch", key = "#userId"),
            @CacheEvict(value = "collectionPois", allEntries = true)
    })
    public void deleteCollection(Long userId, Long collectionId) {
        log.info("Deleting collection {} for user {}", collectionId, userId);

        Collection collection = getCollectionEntity(userId, collectionId);

        // Удаляем все связи с POI
        collectionPoiRepository.deleteAllByCollectionId(collectionId);

        // Удаляем саму коллекцию
        collectionRepository.delete(collection);

        log.debug("Collection deleted successfully");
    }

    @Override
    @Cacheable(value = "collection", key = "#collectionId")
    @Transactional(readOnly = true)
    public CollectionResponse getCollection(Long userId, Long collectionId) {
        log.info("Getting collection {} for user {}", collectionId, userId);

        Collection collection = getCollectionEntity(userId, collectionId);
        CollectionResponse response = mapToResponse(collection);

        // Добавляем количество POI в коллекции
        long poiCount = collectionPoiRepository.countByCollectionId(collectionId);
        response.setPoiCount((int) poiCount);

        return response;
    }

    @Override
    @Cacheable(value = "collections", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<CollectionResponse> getUserCollections(Long userId, Pageable pageable) {
        log.info("Fetching collections for user {}", userId);

        return collectionRepository.findByUserId(userId, pageable)
                .map(collection -> {
                    CollectionResponse response = mapToResponse(collection);
                    long poiCount = collectionPoiRepository.countByCollectionId(collection.getId());
                    response.setPoiCount((int) poiCount);
                    return response;
                });
    }

    @Override
    @Cacheable(value = "collectionSearch", key = "#userId + '-' + #searchTerm")
    @Transactional(readOnly = true)
    public List<CollectionResponse> searchCollections(Long userId, String searchTerm) {
        log.info("Searching collections for user {} with term: {}", userId, searchTerm);

        return collectionRepository.findByUserIdAndNameContainingIgnoreCase(userId, searchTerm)
                .stream()
                .map(collection -> {
                    CollectionResponse response = mapToResponse(collection);
                    long poiCount = collectionPoiRepository.countByCollectionId(collection.getId());
                    response.setPoiCount((int) poiCount);
                    return response;
                })
                .toList();
    }

    @Override
    @CacheEvict(value = {"collection", "collectionPois"}, key = "#collectionId")
    public void addPoiToCollection(Long userId, Long collectionId, CollectionPoiRequest request) {
        log.info("Adding POI {} to collection {} for user {}", request.getPoiId(), collectionId, userId);

        // Проверяем существование коллекции и права доступа
        getCollectionEntity(userId, collectionId);

        // Проверяем, не добавлен ли уже POI в коллекцию
        if (collectionPoiRepository.existsByCollectionIdAndPoiId(collectionId, request.getPoiId())) {
            throw new IllegalStateException("POI already in collection");
        }

        // Определяем порядковый номер
        Integer orderIndex = request.getOrderIndex();
        if (orderIndex == null) {
            long currentCount = collectionPoiRepository.countByCollectionId(collectionId);
            orderIndex = (int) currentCount;
        }

        CollectionPoi collectionPoi = CollectionPoi.builder()
                .collectionId(collectionId)
                .poiId(request.getPoiId())
                .orderIndex(orderIndex)
                .createdAt(LocalDateTime.now())
                .build();

        collectionPoiRepository.save(collectionPoi);
        log.debug("POI added to collection successfully");
    }

    @Override
    @CacheEvict(value = {"collection", "collectionPois"}, key = "#collectionId")
    public void removePoiFromCollection(Long userId, Long collectionId, Long poiId) {
        log.info("Removing POI {} from collection {} for user {}", poiId, collectionId, userId);

        // Проверяем существование коллекции и права доступа
        getCollectionEntity(userId, collectionId);

        // Удаляем связь
        collectionPoiRepository.deleteFromCollection(collectionId, poiId);

        log.debug("POI removed from collection successfully");
    }

    @Override
    @CacheEvict(value = "collectionPois", key = "#collectionId")
    public void updatePoiOrder(Long userId, Long collectionId, Long collectionPoiId, Integer orderIndex) {
        log.info("Updating order for collection item {} in collection {} to {}",
                collectionPoiId, collectionId, orderIndex);

        // Проверяем существование коллекции и права доступа
        getCollectionEntity(userId, collectionId);

        collectionPoiRepository.updateOrderIndex(collectionPoiId, orderIndex);
    }

    @Override
    @Cacheable(value = "collectionPois", key = "#collectionId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<Long> getCollectionPois(Long collectionId, Pageable pageable) {
        log.debug("Fetching POIs for collection {}", collectionId);

        return collectionPoiRepository.findByCollectionId(collectionId, pageable)
                .map(CollectionPoi::getPoiId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getCollectionPoiCount(Long collectionId) {
        log.debug("Getting POI count for collection {}", collectionId);

        return collectionPoiRepository.countByCollectionId(collectionId);
    }

    private Collection getCollectionEntity(Long userId, Long collectionId) {
        return collectionRepository.findById(collectionId)
                .filter(collection -> collection.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
    }

    private CollectionResponse mapToResponse(Collection collection) {
        return CollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .coverUrl(collection.getCoverUrl())
                .userId(collection.getUserId())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }
}