package com.travelapp.personalization.service;

import com.travelapp.personalization.model.dto.request.SearchHistoryRequest;
import com.travelapp.personalization.model.dto.response.SearchHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SearchHistoryService {

    void recordSearch(Long userId, SearchHistoryRequest request);

    Page<SearchHistoryResponse> getUserSearchHistory(Long userId, Pageable pageable);

    Page<SearchHistoryResponse> getUserCitySearchHistory(Long userId, Long cityId, Pageable pageable);

    List<String> getRecentQueries(Long userId, int limit);

    void clearUserHistory(Long userId);

    void cleanupOldHistory();
}