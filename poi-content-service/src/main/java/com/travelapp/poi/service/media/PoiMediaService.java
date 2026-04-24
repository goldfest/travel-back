package com.travelapp.poi.service.media;

import com.travelapp.poi.model.dto.response.PoiMediaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PoiMediaService {

    List<PoiMediaResponse> uploadByAdmin(Long poiId, List<MultipartFile> files, Long adminId);

    List<PoiMediaResponse> uploadByUser(Long poiId, List<MultipartFile> files, Long userId);

    List<PoiMediaResponse> getApprovedMedia(Long poiId);

    Page<PoiMediaResponse> getPendingMedia(Pageable pageable);

    PoiMediaResponse approveMedia(Long poiId, Long mediaId, Long moderatorId);

    PoiMediaResponse rejectMedia(Long poiId, Long mediaId, String reason, Long moderatorId);

    void deleteMedia(Long poiId, Long mediaId, Long userId);
}
