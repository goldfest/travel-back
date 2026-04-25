package com.travelapp.review.service.media;

import com.travelapp.review.config.ReviewMediaStorageProperties;
import com.travelapp.review.model.entity.Report;
import com.travelapp.review.model.entity.ReportMedia;
import com.travelapp.review.model.entity.Review;
import com.travelapp.review.model.entity.ReviewMedia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewMediaStorageService {

    private final ReviewMediaStorageProperties properties;

    public List<ReviewMedia> createReviewMedia(Review review,
                                               Long userId,
                                               List<MultipartFile> files,
                                               ReviewMedia.ModerationStatus status) {
        List<ReviewMedia> result = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            return result;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            StoredFile storedFile = storeFile("reviews", review.getId(), file);

            ReviewMedia media = ReviewMedia.builder()
                    .imageUrl(storedFile.publicUrl())
                    .sourceType(ReviewMedia.SourceType.USER_UPLOAD)
                    .moderationStatus(status)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .userId(userId)
                    .review(review)
                    .build();

            result.add(media);
        }

        return result;
    }

    public List<ReportMedia> createReportMedia(Report report,
                                               Long userId,
                                               List<MultipartFile> files) {
        List<ReportMedia> result = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            return result;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            StoredFile storedFile = storeFile("reports", report.getId(), file);

            ReportMedia media = ReportMedia.builder()
                    .fileUrl(storedFile.publicUrl())
                    .originalFilename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadedByUserId(userId)
                    .report(report)
                    .build();

            result.add(media);
        }

        return result;
    }

    private StoredFile storeFile(String folderType, Long ownerId, MultipartFile file) {
        validateImageFile(file);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "image"
        );
        String extension = resolveExtension(originalFilename, file.getContentType());
        String storedFilename = UUID.randomUUID() + extension;

        Path ownerDir = Paths.get(properties.getUploadDir(), folderType, String.valueOf(ownerId))
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(ownerDir);
            Path targetPath = ownerDir.resolve(storedFilename).normalize();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String publicUrl = properties.getPublicUrlPrefix()
                    + "/" + folderType
                    + "/" + ownerId
                    + "/" + storedFilename;

            return new StoredFile(targetPath, publicUrl);
        } catch (IOException exception) {
            log.error("Failed to store uploaded media file", exception);
            throw new IllegalStateException("Failed to store uploaded media file");
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }

        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("Image file is too large. Max size is " + properties.getMaxFileSizeBytes() + " bytes");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("Image content type is required");
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (!normalized.equals("image/jpeg")
                && !normalized.equals("image/png")
                && !normalized.equals("image/webp")) {
            throw new IllegalArgumentException("Only JPEG, PNG and WEBP images are allowed");
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String extension = null;
        int dotIndex = originalFilename.lastIndexOf('.');

        if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(dotIndex).toLowerCase(Locale.ROOT);
        }

        if (extension == null || extension.isBlank() || extension.length() > 10) {
            String normalizedContentType = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
            if (normalizedContentType.equals("image/png")) {
                return ".png";
            }
            if (normalizedContentType.equals("image/webp")) {
                return ".webp";
            }
            return ".jpg";
        }

        return extension;
    }

    private record StoredFile(Path path, String publicUrl) {
    }
}