package com.travelapp.personalization.service.impl;

import com.travelapp.personalization.model.dto.request.SearchHistoryRequest;
import com.travelapp.personalization.model.dto.response.SearchHistoryResponse;
import com.travelapp.personalization.model.entity.SearchHistory;
import com.travelapp.personalization.repository.SearchHistoryRepository;
import com.travelapp.personalization.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;

    @Override
    @CacheEvict(value = {"searchHistory", "recentQueries"}, key = "#userId")
    public void recordSearch(Long userId, SearchHistoryRequest request) {
        log.info("Recording search for user {}: {}", userId, request.getQueryText());

        SearchHistory searchHistory = SearchHistory.builder()
                .userId(userId)
                .queryText(request.getQueryText())
                .filtersJson(request.getFiltersJson())
                .cityId(request.getCityId())
                .presetFilterId(request.getPresetFilterId())
                .searchedAt(LocalDateTime.now())
                .build();

        searchHistoryRepository.save(searchHistory);
        log.debug("Search recorded successfully");
    }

    @Override
    @Cacheable(value = "searchHistory", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<SearchHistoryResponse> getUserSearchHistory(Long userId, Pageable pageable) {
        log.info("Fetching search history for user {}", userId);

        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "searchHistory", key = "#userId + '-city-' + #cityId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<SearchHistoryResponse> getUserCitySearchHistory(Long userId, Long cityId, Pageable pageable) {
        log.info("Fetching search history for user {} in city {}", userId, cityId);

        return searchHistoryRepository.findByUserIdAndCityId(userId, cityId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "recentQueries", key = "#userId")
    @Transactional(readOnly = true)
    public List<String> getRecentQueries(Long userId, int limit) {
        log.debug("Getting recent queries for user {} (limit: {})", userId, limit);

        return searchHistoryRepository.findTop10ByUserIdOrderBySearchedAtDesc(userId)
                .stream()
                .map(SearchHistory::getQueryText)
                .filter(query -> query != null && !query.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    @Override
    @CacheEvict(value = {"searchHistory", "recentQueries"}, key = "#userId")
    public void clearUserHistory(Long userId) {
        log.info("Clearing search history for user {}", userId);

        searchHistoryRepository.deleteByUserId(userId);
        log.debug("Search history cleared successfully");
    }

    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Ежедневно в 2:00 ночи
    @CacheEvict(value = {"searchHistory", "recentQueries"}, allEntries = true)
    public void cleanupOldHistory(int daysToKeep) {
        log.info("Cleaning up search history older than {} days", daysToKeep);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        searchHistoryRepository.deleteOldHistory(cutoffDate);

        log.info("Old search history cleanup completed");
    }

    private SearchHistoryResponse mapToResponse(SearchHistory searchHistory) {
        return SearchHistoryResponse.builder()
                .id(searchHistory.getId())
                .queryText(searchHistory.getQueryText())
                .filtersJson(searchHistory.getFiltersJson())
                .userId(searchHistory.getUserId())
                .cityId(searchHistory.getCityId())
                .presetFilterId(searchHistory.getPresetFilterId())
                .searchedAt(searchHistory.getSearchedAt())
                .build();
    }
}