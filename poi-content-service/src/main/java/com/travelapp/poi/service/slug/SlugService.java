package com.travelapp.poi.service.slug;

import com.travelapp.poi.repository.PoiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlugService {

    private final PoiRepository poiRepository;

    public String makeUniqueSlug(String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) {
            throw new IllegalArgumentException("Base slug must not be blank");
        }

        if (poiRepository.findBySlug(baseSlug).isEmpty()) {
            return baseSlug;
        }

        int counter = 2;
        String candidate = baseSlug + "-" + counter;

        while (poiRepository.findBySlug(candidate).isPresent()) {
            counter++;
            candidate = baseSlug + "-" + counter;
        }

        return candidate;
    }
}