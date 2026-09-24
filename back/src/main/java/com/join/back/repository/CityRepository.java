package com.join.back.repository;

import com.join.back.model.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<City> findAllByOrderByNameAsc();
}
