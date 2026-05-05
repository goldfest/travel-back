package com.travelapp.personalization.service;

import com.travelapp.personalization.client.PoiClient;
import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.external.PoiDto;
import com.travelapp.personalization.model.dto.request.CollectionPoiRequest;
import com.travelapp.personalization.model.dto.request.CollectionRequest;
import com.travelapp.personalization.model.dto.response.CollectionResponse;
import com.travelapp.personalization.model.entity.Collection;
import com.travelapp.personalization.model.entity.CollectionPoi;
import com.travelapp.personalization.repository.CollectionPoiRepository;
import com.travelapp.personalization.repository.CollectionRepository;
import com.travelapp.personalization.service.impl.CollectionServiceImpl;
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
class CollectionServiceImplTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionPoiRepository collectionPoiRepository;

    @Mock
    private PoiClient poiClient;

    @InjectMocks
    private CollectionServiceImpl collectionService;

    @Test
    void createCollection_shouldSaveCollection_whenNameIsUnique() {
        Long userId = 10L;
        CollectionRequest request = new CollectionRequest("Музеи", "Интересные музеи", "https://example.com/cover.jpg");
        Collection saved = collection(1L, userId, "Музеи");

        when(collectionRepository.existsByUserIdAndName(userId, request.getName())).thenReturn(false);
        when(collectionRepository.save(any(Collection.class))).thenReturn(saved);
        when(collectionPoiRepository.countByCollectionId(1L)).thenReturn(0L);

        CollectionResponse result = collectionService.createCollection(userId, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Музеи");
        assertThat(result.getPoiCount()).isZero();

        verify(collectionRepository).save(argThat(collection ->
                collection.getUserId().equals(userId)
                        && collection.getName().equals("Музеи")
                        && collection.getDescription().equals("Интересные музеи")
        ));
    }

    @Test
    void createCollection_shouldThrowIllegalStateException_whenNameAlreadyExists() {
        Long userId = 10L;
        CollectionRequest request = new CollectionRequest("Музеи", "Описание", null);

        when(collectionRepository.existsByUserIdAndName(userId, request.getName())).thenReturn(true);

        assertThatThrownBy(() -> collectionService.createCollection(userId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(collectionRepository, never()).save(any(Collection.class));
    }

    @Test
    void updateCollection_shouldUpdateCollection_whenUserOwnsItAndNameIsUnique() {
        Long userId = 10L;
        Long collectionId = 1L;
        Collection existing = collection(collectionId, userId, "Старое название");
        CollectionRequest request = new CollectionRequest("Новое название", "Новое описание", "https://example.com/new.jpg");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(existing));
        when(collectionRepository.existsByUserIdAndName(userId, request.getName())).thenReturn(false);
        when(collectionRepository.save(existing)).thenReturn(existing);
        when(collectionPoiRepository.countByCollectionId(collectionId)).thenReturn(2L);

        CollectionResponse result = collectionService.updateCollection(userId, collectionId, request);

        assertThat(result.getName()).isEqualTo("Новое название");
        assertThat(result.getDescription()).isEqualTo("Новое описание");
        assertThat(result.getPoiCount()).isEqualTo(2);
        verify(collectionRepository).save(existing);
    }

    @Test
    void updateCollection_shouldThrowResourceNotFoundException_whenUserDoesNotOwnCollection() {
        Long collectionId = 1L;
        Collection existing = collection(collectionId, 99L, "Чужая коллекция");
        CollectionRequest request = new CollectionRequest("Новое название", null, null);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> collectionService.updateCollection(10L, collectionId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Collection not found");

        verify(collectionRepository, never()).save(any(Collection.class));
    }

    @Test
    void deleteCollection_shouldDeleteRelationsAndCollection_whenCollectionExists() {
        Long userId = 10L;
        Long collectionId = 1L;
        Collection collection = collection(collectionId, userId, "Музеи");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        collectionService.deleteCollection(userId, collectionId);

        verify(collectionPoiRepository).deleteAllByCollectionId(collectionId);
        verify(collectionRepository).delete(collection);
    }

    @Test
    void getCollection_shouldReturnCollectionWithPoiCount() {
        Long userId = 10L;
        Long collectionId = 1L;
        Collection collection = collection(collectionId, userId, "Музеи");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionPoiRepository.countByCollectionId(collectionId)).thenReturn(5L);

        CollectionResponse result = collectionService.getCollection(userId, collectionId);

        assertThat(result.getId()).isEqualTo(collectionId);
        assertThat(result.getPoiCount()).isEqualTo(5);
    }

    @Test
    void getUserCollections_shouldReturnMappedPage() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);
        Collection collection = collection(1L, userId, "Музеи");

        when(collectionRepository.findByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(collection), pageable, 1));
        when(collectionPoiRepository.countByCollectionId(1L)).thenReturn(3L);

        Page<CollectionResponse> result = collectionService.getUserCollections(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPoiCount()).isEqualTo(3);
    }

    @Test
    void searchCollections_shouldReturnMappedList() {
        Long userId = 10L;
        Collection collection = collection(1L, userId, "Музеи Ульяновска");

        when(collectionRepository.findByUserIdAndNameContainingIgnoreCase(userId, "музеи"))
                .thenReturn(List.of(collection));
        when(collectionPoiRepository.countByCollectionId(1L)).thenReturn(1L);

        List<CollectionResponse> result = collectionService.searchCollections(userId, "музеи");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Музеи Ульяновска");
    }

    @Test
    void addPoiToCollection_shouldUseCurrentCountAsOrderIndex_whenOrderIndexIsNull() {
        Long userId = 10L;
        Long collectionId = 1L;
        Long poiId = 100L;
        Collection collection = collection(collectionId, userId, "Музеи");
        CollectionPoiRequest request = new CollectionPoiRequest(poiId, null);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(poiClient.getPoiById(poiId)).thenReturn(poi(poiId));
        when(collectionPoiRepository.existsByCollectionIdAndPoiId(collectionId, poiId)).thenReturn(false);
        when(collectionPoiRepository.countByCollectionId(collectionId)).thenReturn(4L);

        collectionService.addPoiToCollection(userId, collectionId, request);

        verify(collectionPoiRepository).save(argThat(item ->
                item.getCollectionId().equals(collectionId)
                        && item.getPoiId().equals(poiId)
                        && item.getOrderIndex().equals(4)
        ));
    }

    @Test
    void addPoiToCollection_shouldThrowResourceNotFoundException_whenPoiDoesNotExist() {
        Long userId = 10L;
        Long collectionId = 1L;
        Long poiId = 404L;
        Collection collection = collection(collectionId, userId, "Музеи");
        CollectionPoiRequest request = new CollectionPoiRequest(poiId, null);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(poiClient.getPoiById(poiId)).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> collectionService.addPoiToCollection(userId, collectionId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("POI not found");

        verify(collectionPoiRepository, never()).save(any(CollectionPoi.class));
    }

    @Test
    void addPoiToCollection_shouldThrowIllegalStateException_whenPoiAlreadyInCollection() {
        Long userId = 10L;
        Long collectionId = 1L;
        Long poiId = 100L;
        Collection collection = collection(collectionId, userId, "Музеи");
        CollectionPoiRequest request = new CollectionPoiRequest(poiId, 1);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(poiClient.getPoiById(poiId)).thenReturn(poi(poiId));
        when(collectionPoiRepository.existsByCollectionIdAndPoiId(collectionId, poiId)).thenReturn(true);

        assertThatThrownBy(() -> collectionService.addPoiToCollection(userId, collectionId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in collection");

        verify(collectionPoiRepository, never()).save(any(CollectionPoi.class));
    }

    @Test
    void removePoiFromCollection_shouldCheckOwnerAndDeleteRelation() {
        Long userId = 10L;
        Long collectionId = 1L;
        Long poiId = 100L;
        Collection collection = collection(collectionId, userId, "Музеи");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        collectionService.removePoiFromCollection(userId, collectionId, poiId);

        verify(collectionPoiRepository).deleteFromCollection(collectionId, poiId);
    }

    @Test
    void updatePoiOrder_shouldCheckOwnerAndUpdateOrderIndex() {
        Long userId = 10L;
        Long collectionId = 1L;
        Long collectionPoiId = 50L;
        Collection collection = collection(collectionId, userId, "Музеи");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        collectionService.updatePoiOrder(userId, collectionId, collectionPoiId, 7);

        verify(collectionPoiRepository).updateOrderIndex(collectionPoiId, 7);
    }

    @Test
    void getCollectionPois_shouldReturnPoiIds() {
        Long userId = 10L;
        Long collectionId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        CollectionPoi item = CollectionPoi.builder()
                .id(50L)
                .collectionId(collectionId)
                .poiId(100L)
                .orderIndex(0)
                .build();

        when(collectionPoiRepository.findByCollectionId(collectionId, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        Page<Long> result = collectionService.getCollectionPois(userId, collectionId, pageable);

        assertThat(result.getContent()).containsExactly(100L);
    }

    @Test
    void getCollectionPoiCount_shouldCheckOwnerAndReturnCount() {
        Long userId = 10L;
        Long collectionId = 1L;
        Collection collection = collection(collectionId, userId, "Музеи");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionPoiRepository.countByCollectionId(collectionId)).thenReturn(8L);

        Long result = collectionService.getCollectionPoiCount(userId, collectionId);

        assertThat(result).isEqualTo(8L);
    }

    private Collection collection(Long id, Long userId, String name) {
        return Collection.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .description("Описание")
                .coverUrl("https://example.com/cover.jpg")
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 12, 0))
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
