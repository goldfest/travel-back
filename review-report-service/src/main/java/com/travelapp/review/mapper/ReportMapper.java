package com.travelapp.review.mapper;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.model.entity.Report;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ReportMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "pending")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "handledAt", ignore = true)
    @Mapping(target = "handledByUserId", ignore = true)
    Report toEntity(CreateReportRequest request);

    ReportResponse toResponse(Report report);

    List<ReportResponse> toResponseList(List<Report> reports);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportType", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "reviewId", ignore = true)
    @Mapping(target = "poiId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "handledAt", expression = "java(request.getStatus() != null && !request.getStatus().equals(\"pending\") ? java.time.LocalDateTime.now() : null)")
    void updateEntity(@MappingTarget Report report, UpdateReportRequest request);

    default ReportResponse toResponseWithDetails(Report report, String handledByUserName, String targetTitle) {
        ReportResponse response = toResponse(report);
        response.setHandledByUserName(handledByUserName);
        response.setTargetTitle(targetTitle);

        // Определяем тип цели
        if (report.getReviewId() != null) {
            response.setTargetType("review");
        } else if (report.getPoiId() != null) {
            response.setTargetType("poi");
        }

        return response;
    }
}