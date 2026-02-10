package com.travelapp.personalization.service;

import com.travelapp.personalization.model.dto.request.CollectionPoiRequest;
import com.travelapp.personalization.model.dto.request.CollectionRequest;
import com.travelapp.personalization.model.dto.response.CollectionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CollectionService {

    CollectionResponse createCollection(Long userId, CollectionRequest request);

    CollectionResponse updateCollection(Long userId, Long collectionId, CollectionRequest request);

    void deleteCollection(Long userId, Long collectionId);

    CollectionResponse getCollection(Long userId, Long collectionId);

    Page<CollectionResponse> getUserCollections(Long userId, Pageable pageable);

    List<CollectionResponse> searchCollections(Long userId, String searchTerm);

    void addPoiToCollection(Long userId, Long collectionId, CollectionPoiRequest request);

    void removePoiFromCollection(Long userId, Long collectionId, Long poiId);

    void updatePoiOrder(Long userId, Long collectionId, Long collectionPoiId, Integer orderIndex);

    Page<Long> getCollectionPois(Long collectionId, Pageable pageable);

    Long getCollectionPoiCount(Long collectionId);
}