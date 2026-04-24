package com.travelapp.poi.controller;

import com.travelapp.poi.model.dto.request.PoiMediaRejectRequest;
import com.travelapp.poi.model.dto.response.PoiMediaResponse;
import com.travelapp.poi.security.SecurityUtils;
import com.travelapp.poi.service.media.PoiMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/pois")
@RequiredArgsConstructor
@Tag(name = "POI Media Management", description = "Endpoints for uploading and moderating POI photos")
public class PoiMediaController {

    private final PoiMediaService poiMediaService;

    @PostMapping(value = "/{poiId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload POI photos by user", description = "User uploaded photos are saved with PENDING moderation status")
    public ResponseEntity<List<PoiMediaResponse>> uploadUserPhotos(
            @PathVariable Long poiId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(poiMediaService.uploadByUser(poiId, files, userId));
    }

    @PostMapping(value = "/{poiId}/media/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload POI photos by admin", description = "Admin or moderator photos are approved immediately and displayed first")
    public ResponseEntity<List<PoiMediaResponse>> uploadAdminPhotos(
            @PathVariable Long poiId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        Long adminId = SecurityUtils.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(poiMediaService.uploadByAdmin(poiId, files, adminId));
    }

    @GetMapping("/{poiId}/media")
    @Operation(summary = "Get approved POI photos", description = "Returns only approved photos sorted by display priority")
    public ResponseEntity<List<PoiMediaResponse>> getApprovedMedia(@PathVariable Long poiId) {
        return ResponseEntity.ok(poiMediaService.getApprovedMedia(poiId));
    }

    @GetMapping("/media/pending")
    @Operation(summary = "Get pending POI photos", description = "Admin/moderator endpoint for photo moderation")
    public ResponseEntity<Page<PoiMediaResponse>> getPendingMedia(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(poiMediaService.getPendingMedia(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/{poiId}/media/{mediaId}/approve")
    @Operation(summary = "Approve POI photo", description = "Approves user uploaded photo and makes it visible in POI card")
    public ResponseEntity<PoiMediaResponse> approveMedia(
            @PathVariable Long poiId,
            @PathVariable Long mediaId
    ) {
        Long moderatorId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(poiMediaService.approveMedia(poiId, mediaId, moderatorId));
    }

    @PostMapping("/{poiId}/media/{mediaId}/reject")
    @Operation(summary = "Reject POI photo", description = "Rejects uploaded photo and hides it from POI card")
    public ResponseEntity<PoiMediaResponse> rejectMedia(
            @PathVariable Long poiId,
            @PathVariable Long mediaId,
            @Valid @RequestBody(required = false) PoiMediaRejectRequest request
    ) {
        Long moderatorId = SecurityUtils.requireUserId();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(poiMediaService.rejectMedia(poiId, mediaId, reason, moderatorId));
    }

    @DeleteMapping("/{poiId}/media/{mediaId}")
    @Operation(summary = "Delete POI photo", description = "Deletes POI photo metadata and local file if it is stored locally")
    public ResponseEntity<Void> deleteMedia(
            @PathVariable Long poiId,
            @PathVariable Long mediaId
    ) {
        Long userId = SecurityUtils.requireUserId();
        poiMediaService.deleteMedia(poiId, mediaId, userId);
        return ResponseEntity.noContent().build();
    }
}
