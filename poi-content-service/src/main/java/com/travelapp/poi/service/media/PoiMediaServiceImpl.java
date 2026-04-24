package com.travelapp.poi.service.media;

import com.travelapp.poi.config.PoiMediaStorageProperties;
import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.exception.ValidationException;
import com.travelapp.poi.model.dto.response.PoiMediaResponse;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.model.entity.PoiMedia;
import com.travelapp.poi.repository.PoiMediaRepository;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiMediaServiceImpl implements PoiMediaService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MimeTypeUtils.IMAGE_JPEG_VALUE,
            MimeTypeUtils.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private final PoiRepository poiRepository;
    private final PoiMediaRepository poiMediaRepository;
    private final PoiMediaStorageProperties storageProperties;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public List<PoiMediaResponse> uploadByAdmin(Long poiId, List<MultipartFile> files, Long adminId) {
        SecurityUtils.requireAdminOrModerator();
        Poi poi = getPoi(poiId);
        return saveFiles(poi, files, adminId, PoiMedia.SourceType.ADMIN_UPLOAD, PoiMedia.ModerationStatus.APPROVED, adminId);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public List<PoiMediaResponse> uploadByUser(Long poiId, List<MultipartFile> files, Long userId) {
        Poi poi = getPoi(poiId);
        return saveFiles(poi, files, userId, PoiMedia.SourceType.USER_UPLOAD, PoiMedia.ModerationStatus.PENDING, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PoiMediaResponse> getApprovedMedia(Long poiId) {
        if (!poiRepository.existsById(poiId)) {
            throw new PoiNotFoundException(poiId);
        }
        return poiMediaRepository.findByPoiIdAndModerationStatus(poiId, PoiMedia.ModerationStatus.APPROVED).stream()
                .sorted(Comparator
                        .comparingInt(this::mediaPriority)
                        .thenComparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder())
                        .thenComparing(PoiMedia::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiMediaResponse> getPendingMedia(Pageable pageable) {
        SecurityUtils.requireAdminOrModerator();
        return poiMediaRepository.findByModerationStatus(PoiMedia.ModerationStatus.PENDING, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public PoiMediaResponse approveMedia(Long poiId, Long mediaId, Long moderatorId) {
        SecurityUtils.requireAdminOrModerator();
        PoiMedia media = getMedia(poiId, mediaId);
        media.setModerationStatus(PoiMedia.ModerationStatus.APPROVED);
        media.setRejectionReason(null);
        media.setModeratedBy(moderatorId);
        media.setModeratedAt(LocalDateTime.now());
        return toResponse(poiMediaRepository.save(media));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public PoiMediaResponse rejectMedia(Long poiId, Long mediaId, String reason, Long moderatorId) {
        SecurityUtils.requireAdminOrModerator();
        PoiMedia media = getMedia(poiId, mediaId);
        media.setModerationStatus(PoiMedia.ModerationStatus.REJECTED);
        media.setRejectionReason(StringUtils.trimToNull(reason));
        media.setModeratedBy(moderatorId);
        media.setModeratedAt(LocalDateTime.now());
        return toResponse(poiMediaRepository.save(media));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public void deleteMedia(Long poiId, Long mediaId, Long userId) {
        SecurityUtils.requireAdminOrModerator();
        PoiMedia media = getMedia(poiId, mediaId);
        poiMediaRepository.delete(media);
        deleteLocalFileIfPossible(media.getUrl());
    }

    private int mediaPriority(PoiMedia media) {
        if (media == null || media.getSourceType() == null) return 99;
        return switch (media.getSourceType()) {
            case ADMIN_UPLOAD -> 0;
            case SYSTEM_WIKIMEDIA -> 1;
            case USER_UPLOAD -> 2;
        };
    }

    private List<PoiMediaResponse> saveFiles(
            Poi poi,
            List<MultipartFile> files,
            Long uploadedBy,
            PoiMedia.SourceType sourceType,
            PoiMedia.ModerationStatus moderationStatus,
            Long moderatorId
    ) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one photo must be provided");
        }

        return files.stream()
                .map(file -> saveOneFile(poi, file, uploadedBy, sourceType, moderationStatus, moderatorId))
                .map(this::toResponse)
                .toList();
    }

    private PoiMedia saveOneFile(
            Poi poi,
            MultipartFile file,
            Long uploadedBy,
            PoiMedia.SourceType sourceType,
            PoiMedia.ModerationStatus moderationStatus,
            Long moderatorId
    ) {
        validateFile(file);

        String originalFilename = StringUtils.defaultString(file.getOriginalFilename(), "image");
        String contentType = StringUtils.defaultString(file.getContentType(), MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
        String extension = resolveExtension(originalFilename, contentType);
        String storedFilename = UUID.randomUUID() + extension;

        Path poiDir = Paths.get(storageProperties.getUploadDir())
                .toAbsolutePath()
                .normalize()
                .resolve(String.valueOf(poi.getId()));

        try {
            Files.createDirectories(poiDir);
            Path target = poiDir.resolve(storedFilename).normalize();
            if (!target.startsWith(poiDir)) {
                throw new ValidationException("Invalid file path");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("Failed to store POI media file: poiId={}, originalFilename={}", poi.getId(), originalFilename, ex);
            throw new ValidationException("Failed to store photo file");
        }

        PoiMedia media = new PoiMedia();
        media.setPoi(poi);
        media.setUrl(buildPublicUrl(poi.getId(), storedFilename));
        media.setMediaType(PoiMedia.MediaType.PHOTO);
        media.setSourceType(sourceType);
        media.setModerationStatus(moderationStatus);
        media.setUserId(uploadedBy);
        media.setOriginalFilename(StringUtils.abbreviate(originalFilename, 255));
        media.setContentType(contentType);
        media.setFileSize(file.getSize());

        if (moderationStatus == PoiMedia.ModerationStatus.APPROVED) {
            media.setModeratedBy(moderatorId);
            media.setModeratedAt(LocalDateTime.now());
        }

        return poiMediaRepository.save(media);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Photo file is empty");
        }

        if (file.getSize() > storageProperties.getMaxFileSizeBytes()) {
            throw new ValidationException("Photo file is too large. Max size is " + storageProperties.getMaxFileSizeBytes() + " bytes");
        }

        String contentType = file.getContentType();
        if (StringUtils.isBlank(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("Only JPEG, PNG and WEBP images are allowed");
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return ".jpg";
        if (lowerName.endsWith(".png")) return ".png";
        if (lowerName.endsWith(".webp")) return ".webp";

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case MimeTypeUtils.IMAGE_JPEG_VALUE -> ".jpg";
            case MimeTypeUtils.IMAGE_PNG_VALUE -> ".png";
            case "image/webp" -> ".webp";
            default -> ".img";
        };
    }

    private String buildPublicUrl(Long poiId, String storedFilename) {
        String prefix = storageProperties.getPublicUrlPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + poiId + "/" + storedFilename;
    }

    private Poi getPoi(Long poiId) {
        return poiRepository.findById(poiId)
                .orElseThrow(() -> new PoiNotFoundException(poiId));
    }

    private PoiMedia getMedia(Long poiId, Long mediaId) {
        return poiMediaRepository.findByIdAndPoiId(mediaId, poiId)
                .orElseThrow(() -> new ValidationException("POI media not found: " + mediaId));
    }

    private PoiMediaResponse toResponse(PoiMedia media) {
        PoiMediaResponse response = new PoiMediaResponse();
        response.setId(media.getId());
        response.setPoiId(media.getPoi() != null ? media.getPoi().getId() : null);
        response.setUrl(media.getUrl());
        response.setMediaType(media.getMediaType());
        response.setSourceType(media.getSourceType());
        response.setModerationStatus(media.getModerationStatus());
        response.setDisplayOrder(media.getDisplayOrder());
        response.setOriginalFilename(media.getOriginalFilename());
        response.setContentType(media.getContentType());
        response.setFileSize(media.getFileSize());
        response.setRejectionReason(media.getRejectionReason());
        response.setUserId(media.getUserId());
        response.setModeratedBy(media.getModeratedBy());
        response.setModeratedAt(media.getModeratedAt());
        response.setCreatedAt(media.getCreatedAt());
        return response;
    }

    private void deleteLocalFileIfPossible(String url) {
        if (StringUtils.isBlank(url)) {
            return;
        }

        String prefix = storageProperties.getPublicUrlPrefix();
        if (!url.startsWith(prefix)) {
            return;
        }

        String relative = url.substring(prefix.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        Path target = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(relative).normalize();
        Path root = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Failed to delete local media file: {}", target, ex);
        }
    }
}
