package com.travelapp.poi.service.impl;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiSourceRepository;
import com.travelapp.poi.service.PoiDuplicateDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiDuplicateDetectionServiceImpl implements PoiDuplicateDetectionService {

    /**
     * About 110 metres by latitude. This is enough for API jitter, but not enough
     * to merge two different objects placed in the same district.
     */
    private static final BigDecimal COORDINATE_DELTA = new BigDecimal("0.001");
    private static final double STRONG_NAME_SIMILARITY = 0.88;
    private static final double WEAK_NAME_SIMILARITY_WITH_ADDRESS = 0.78;

    private final PoiRepository poiRepository;
    private final PoiSourceRepository poiSourceRepository;

    @Override
    public Optional<Poi> findDuplicate(PoiCreateRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        Optional<Poi> bySource = findByExternalId(request);
        if (bySource.isPresent()) {
            log.info("Duplicate detected by externalId for POI name={}", request.getName());
            return bySource;
        }

        Optional<Poi> bySourceUrl = findBySourceUrl(request);
        if (bySourceUrl.isPresent()) {
            log.info("Duplicate detected by sourceUrl for POI name={}", request.getName());
            return bySourceUrl;
        }

        Optional<Poi> byNameAndAddress = findByNameAndAddress(request);
        if (byNameAndAddress.isPresent()) {
            log.info("Duplicate detected by normalized name+address for POI name={}", request.getName());
            return byNameAndAddress;
        }

        Optional<Poi> byNameAndCoordinates = findByNameAndCoordinates(request);
        if (byNameAndCoordinates.isPresent()) {
            log.info("Duplicate detected by normalized name+coordinates for POI name={}", request.getName());
            return byNameAndCoordinates;
        }

        return Optional.empty();
    }

    private Optional<Poi> findByExternalId(PoiCreateRequest request) {
        if (request.getSources() == null || request.getSources().isEmpty()) {
            return Optional.empty();
        }

        for (PoiCreateRequest.SourceRequest source : request.getSources()) {
            if (source.getSourceCode() == null || source.getExternalId() == null
                    || source.getSourceCode().isBlank() || source.getExternalId().isBlank()) {
                continue;
            }

            var poiSource = poiSourceRepository.findFirstBySourceCodeIgnoreCaseAndExternalIdIgnoreCase(
                    source.getSourceCode().trim(),
                    source.getExternalId().trim()
            );

            if (poiSource.isPresent() && poiSource.get().getPoi() != null) {
                return Optional.of(poiSource.get().getPoi());
            }
        }

        return Optional.empty();
    }

    private Optional<Poi> findBySourceUrl(PoiCreateRequest request) {
        if (request.getSources() == null || request.getSources().isEmpty()) {
            return Optional.empty();
        }

        for (PoiCreateRequest.SourceRequest source : request.getSources()) {
            if (source.getSourceCode() == null || source.getSourceUrl() == null
                    || source.getSourceCode().isBlank() || source.getSourceUrl().isBlank()) {
                continue;
            }

            var poiSource = poiSourceRepository.findFirstBySourceCodeIgnoreCaseAndSourceUrlIgnoreCase(
                    source.getSourceCode().trim(),
                    normalizeSourceUrl(source.getSourceUrl())
            );

            if (poiSource.isPresent() && poiSource.get().getPoi() != null) {
                return Optional.of(poiSource.get().getPoi());
            }
        }

        return Optional.empty();
    }

    private Optional<Poi> findByNameAndAddress(PoiCreateRequest request) {
        if (request.getName() == null || request.getAddress() == null || request.getCityId() == null
                || request.getName().isBlank() || request.getAddress().isBlank()) {
            return Optional.empty();
        }

        Optional<Poi> exact = poiRepository.findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(
                request.getName().trim(),
                request.getAddress().trim(),
                request.getCityId()
        );

        if (exact.isPresent()) {
            return exact;
        }

        if (request.getLatitude() == null || request.getLongitude() == null) {
            return Optional.empty();
        }

        String incomingName = normalizeTextForCompare(request.getName());
        String incomingAddress = normalizeTextForCompare(request.getAddress());

        return poiRepository.findPotentialDuplicatesByCoordinates(
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getCityId(),
                        COORDINATE_DELTA
                )
                .stream()
                .filter(candidate -> isSimilarNameAndAddress(candidate, incomingName, incomingAddress))
                .min(Comparator.comparing(candidate -> distanceScore(candidate, request)));
    }

    private Optional<Poi> findByNameAndCoordinates(PoiCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()
                || request.getCityId() == null
                || request.getLatitude() == null
                || request.getLongitude() == null) {
            return Optional.empty();
        }

        List<Poi> exactCandidates = poiRepository.findPotentialDuplicatesByNameAndCoordinates(
                request.getName().trim(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCityId(),
                COORDINATE_DELTA
        );

        if (exactCandidates != null && !exactCandidates.isEmpty()) {
            return Optional.of(exactCandidates.get(0));
        }

        String incomingName = normalizeTextForCompare(request.getName());
        String incomingAddress = normalizeTextForCompare(request.getAddress());

        return poiRepository.findPotentialDuplicatesByCoordinates(
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getCityId(),
                        COORDINATE_DELTA
                )
                .stream()
                .filter(candidate -> isDuplicateBySimilarity(candidate, incomingName, incomingAddress))
                .min(Comparator.comparing(candidate -> distanceScore(candidate, request)));
    }

    private boolean isSimilarNameAndAddress(Poi candidate, String incomingName, String incomingAddress) {
        String candidateName = normalizeTextForCompare(candidate.getName());
        String candidateAddress = normalizeTextForCompare(candidate.getAddress());

        return similarity(candidateName, incomingName) >= WEAK_NAME_SIMILARITY_WITH_ADDRESS
                && (StringUtils.isBlank(incomingAddress)
                || StringUtils.isBlank(candidateAddress)
                || candidateAddress.contains(incomingAddress)
                || incomingAddress.contains(candidateAddress)
                || similarity(candidateAddress, incomingAddress) >= 0.75);
    }

    private boolean isDuplicateBySimilarity(Poi candidate, String incomingName, String incomingAddress) {
        String candidateName = normalizeTextForCompare(candidate.getName());
        String candidateAddress = normalizeTextForCompare(candidate.getAddress());

        double nameSimilarity = similarity(candidateName, incomingName);
        if (nameSimilarity >= STRONG_NAME_SIMILARITY) {
            return true;
        }

        return nameSimilarity >= WEAK_NAME_SIMILARITY_WITH_ADDRESS
                && StringUtils.isNotBlank(incomingAddress)
                && StringUtils.isNotBlank(candidateAddress)
                && similarity(candidateAddress, incomingAddress) >= 0.75;
    }

    private BigDecimal distanceScore(Poi candidate, PoiCreateRequest request) {
        BigDecimal latDiff = candidate.getLatitude().subtract(request.getLatitude()).abs();
        BigDecimal lngDiff = candidate.getLongitude().subtract(request.getLongitude()).abs();
        return latDiff.add(lngDiff);
    }

    private String normalizeSourceUrl(String value) {
        return StringUtils.defaultString(value).trim();
    }

    private String normalizeTextForCompare(String value) {
        return StringUtils.defaultString(value)
                .toLowerCase(java.util.Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^а-яa-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double similarity(String left, String right) {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return 0.0;
        }

        if (left.equals(right)) {
            return 1.0;
        }

        int distance = levenshtein(left, right);
        int maxLength = Math.max(left.length(), right.length());
        return maxLength == 0 ? 1.0 : 1.0 - ((double) distance / maxLength);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }
}
