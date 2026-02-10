package com.travelapp.poi.service.mapper;

import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import com.travelapp.poi.model.entity.DataImportTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImportTaskMapper {

    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "startedAt", source = "startedAt")
    @Mapping(target = "finishedAt", source = "finishedAt")
    ImportTaskResponse toResponse(DataImportTask task);
}