package com.join.back.service;

import com.join.back.model.dto.CityResponse;
import com.join.back.model.entity.City;
import com.join.back.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    public List<CityResponse> searchCities(String name) {
        List<City> cities;
        if (name == null || name.isBlank()) {
            cities = cityRepository.findAllByOrderByNameAsc();
        } else {
            cities = cityRepository.findByNameContainingIgnoreCaseOrderByNameAsc(name);
        }
        return cities.stream()
                .map(city -> new CityResponse(city.getId(), city.getName()))
                .toList();
    }
}
