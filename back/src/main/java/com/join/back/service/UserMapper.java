package com.join.back.service;

import com.join.back.model.dto.UserProfileResponse;
import com.join.back.model.dto.UserRegistrationRequest;
import com.join.back.model.dto.UserResponse;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "interests", source = "interests", qualifiedByName = "eventTypesToStrings")
    @Mapping(target = "universityId", source = "university.id")
    @Mapping(target = "universityName", source = "university.name")
    UserResponse toResponse(User user);

    @Mapping(target = "interests", source = "interests", qualifiedByName = "eventTypesToStrings")
    @Mapping(target = "universityId", source = "university.id")
    @Mapping(target = "universityName", source = "university.name")
    @Mapping(target = "hasPassword", expression = "java(user.getPasswordHash() != null)")
    UserProfileResponse toProfileResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "maxId", ignore = true)
    @Mapping(target = "telegramId", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "telegramChannel", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "preferredAgeMin", ignore = true)
    @Mapping(target = "preferredAgeMax", ignore = true)
    @Mapping(target = "preferredGender", ignore = true)
    @Mapping(target = "preferredUniversityId", ignore = true)
    @Mapping(target = "university", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "gender", source = "gender", qualifiedByName = "stringToGender")
    @Mapping(target = "interests", source = "interests", qualifiedByName = "stringsToEventTypes")
    User toEntity(UserRegistrationRequest request);

    @Named("eventTypesToStrings")
    default List<String> eventTypesToStrings(List<EventType> interests) {
        if (interests == null) {
            return Collections.emptyList();
        }
        return interests.stream()
                .map(EventType::name)
                .toList();
    }

    @Named("stringsToEventTypes")
    default List<EventType> stringsToEventTypes(List<String> interests) {
        if (interests == null) {
            return Collections.emptyList();
        }
        return interests.stream()
                .map(this::stringToEventType)
                .toList();
    }

    @Named("stringToGender")
    default Gender stringToGender(String gender) {
        if (gender == null) {
            return null;
        }
        try {
            return Gender.valueOf(gender.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid gender value: " + gender);
        }
    }

    @Named("stringToEventType")
    default EventType stringToEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        try {
            return EventType.valueOf(eventType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid interest value: " + eventType);
        }
    }
}
