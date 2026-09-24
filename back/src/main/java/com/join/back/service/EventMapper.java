package com.join.back.service;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.EventDetailResponse;
import com.join.back.model.entity.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "liked", constant = "false")
    @Mapping(target = "hasMatch", constant = "false")
    EventCardResponse toCardResponse(Event event);

    @Mapping(target = "liked", constant = "false")
    @Mapping(target = "hasMatch", constant = "false")
    EventDetailResponse toDetailResponse(Event event);
}
