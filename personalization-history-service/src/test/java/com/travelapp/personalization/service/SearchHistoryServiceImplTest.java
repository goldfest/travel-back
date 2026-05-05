package com.travelapp.personalization.service;

import com.travelapp.personalization.client.CityClient;
import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.external.CityDto;
import com.travelapp.personalization.model.dto.request.SearchHistoryRequest;
import com.travelapp.personalization.model.dto.response.SearchHistoryResponse;
import com.travelapp.personalization.model.entity.SearchHistory;
import com.travelapp.personalization.repository.SearchHistoryRepository;
import com.travelapp.personalization.service.impl.SearchHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceImplTest {

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private CityClient cityClient;

    @InjectMocks
    private SearchHistoryServiceImpl searchHistoryService;

    @Test
    void recordSearch_shouldSaveSearchHistory_whenCityExists() {
        Long userId = 10L;
        SearchHistoryRequest request = new SearchHistoryRequest(
                "музей",
                "{\"ratingFrom\":4}",
                1L,
                5L
        );

        when(cityClient.getCityById(1L)).thenReturn(city(1L));

        searchHistoryService.recordSearch(userId, request);

        verify(searchHistoryRepository).save(argThat(history ->
                history.getUserId().equals(userId)
                        && history.getQueryText().equals("музей")
                        && history.getFiltersJson().equals("{\"ratingFrom\":4}")
                        && history.getCityId().equals(1L)
                        && history.getPresetFilterId().equals(5L)
        ));
    }

    @Test
    void recordSearch_shouldNotCallCityClient_whenCityIdIsNull() {
        Long userId = 10L;
        SearchHistoryRequest request = new SearchHistoryRequest("музей", null, null, null);

        searchHistoryService.recordSearch(userId, request);

        verify(cityClient, never()).getCityById(any());
        verify(searchHistoryRepository).save(any(SearchHistory.class));
    }

    @Test
    void recordSearch_shouldThrowResourceNotFoundException_whenCityDoesNotExist() {
        Long userId = 10L;
        SearchHistoryRequest request = new SearchHistoryRequest("музей", null, 404L, null);

        when(cityClient.getCityById(404L)).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> searchHistoryService.recordSearch(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("City not found");

        verify(searchHistoryRepository, never()).save(any(SearchHistory.class));
    }

    @Test
    void getUserSearchHistory_shouldReturnMappedPage() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);
        SearchHistory history = history(1L, userId, "музей", 1L, 5L);

        when(searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        Page<SearchHistoryResponse> result = searchHistoryService.getUserSearchHistory(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getQueryText()).isEqualTo("музей");
    }

    @Test
    void getUserCitySearchHistory_shouldReturnMappedPage() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);
        SearchHistory history = history(1L, userId, "музей", 1L, 5L);

        when(searchHistoryRepository.findByUserIdAndCityId(userId, 1L, pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        Page<SearchHistoryResponse> result = searchHistoryService.getUserCitySearchHistory(userId, 1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCityId()).isEqualTo(1L);
    }

    @Test
    void getRecentQueries_shouldFilterBlankQueriesRemoveDuplicatesAndLimitResult() {
        Long userId = 10L;
        List<SearchHistory> items = List.of(
                history(1L, userId, "музей", 1L, null),
                history(2L, userId, " ", 1L, null),
                history(3L, userId, "парк", 1L, null),
                history(4L, userId, "музей", 1L, null),
                history(5L, userId, null, 1L, null)
        );

        when(searchHistoryRepository.findTop10ByUserIdOrderBySearchedAtDesc(userId)).thenReturn(items);

        List<String> result = searchHistoryService.getRecentQueries(userId, 2);

        assertThat(result).containsExactly("музей", "парк");
    }

    @Test
    void clearUserHistory_shouldDeleteHistoryByUserId() {
        searchHistoryService.clearUserHistory(10L);

        verify(searchHistoryRepository).deleteByUserId(10L);
    }

    @Test
    void cleanupOldHistory_shouldDeleteHistoryOlderThanConfiguredDays() {
        ReflectionTestUtils.setField(searchHistoryService, "daysToKeep", 45);
        ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        searchHistoryService.cleanupOldHistory();

        verify(searchHistoryRepository).deleteOldHistory(dateCaptor.capture());
        LocalDateTime cutoff = dateCaptor.getValue();
        assertThat(cutoff).isBefore(LocalDateTime.now().minusDays(44));
        assertThat(cutoff).isAfter(LocalDateTime.now().minusDays(46));
    }

    private SearchHistory history(Long id, Long userId, String queryText, Long cityId, Long presetFilterId) {
        return SearchHistory.builder()
                .id(id)
                .userId(userId)
                .queryText(queryText)
                .filtersJson("{\"ratingFrom\":4}")
                .cityId(cityId)
                .presetFilterId(presetFilterId)
                .searchedAt(LocalDateTime.of(2026, 1, 1, 12, 0).plusMinutes(id))
                .build();
    }

    private CityDto city(Long id) {
        CityDto city = new CityDto();
        city.setId(id);
        city.setName("Ульяновск");
        city.setSlug("ulyanovsk");
        city.setCountry("Россия");
        return city;
    }
}
