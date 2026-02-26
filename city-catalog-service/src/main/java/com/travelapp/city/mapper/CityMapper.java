package com.travelapp.city.mapper;

import com.travelapp.city.model.dto.request.CityRequestDto;
import com.travelapp.city.model.dto.response.CityLookupDto;
import com.travelapp.city.model.dto.response.CityResponseDto;
import com.travelapp.city.model.entity.City;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    City toEntity(CityRequestDto dto);

    CityResponseDto toDto(City entity);
    CityLookupDto toLookupDto(City entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CityRequestDto dto, @MappingTarget City entity);

    List<CityResponseDto> toDtoList(List<City> entities);
    List<CityLookupDto> toLookupDtoList(List<City> entities);
}