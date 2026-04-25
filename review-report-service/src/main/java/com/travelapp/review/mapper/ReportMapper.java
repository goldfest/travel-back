package com.travelapp.review.mapper;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.model.entity.Report;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {ReportMediaMapper.class})
public interface ReportMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "handledByUserId", ignore = true)
    @Mapping(target = "handledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "moderatorComment", ignore = true)
    @Mapping(target = "media", ignore = true)
    Report toEntity(CreateReportRequest request);

    @Mapping(target = "media", source = "media")
    @Mapping(target = "totalMediaCount", expression = "java(report.getMedia() != null ? report.getMedia().size() : 0)")
    ReportResponse toResponse(Report report);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "handledByUserId", ignore = true)
    @Mapping(target = "handledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reportType", ignore = true)
    @Mapping(target = "reviewId", ignore = true)
    @Mapping(target = "poiId", ignore = true)
    @Mapping(target = "moderatorComment", ignore = true)
    @Mapping(target = "media", ignore = true)
    void updateEntity(@MappingTarget Report report, com.travelapp.review.model.dto.request.UpdateReportRequest request);
}