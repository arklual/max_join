package com.join.back.service;

import com.join.back.model.dto.SearchCriteriaRequest;
import com.join.back.model.dto.SearchCriteriaResponse;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchCriteriaService {

    private final UserRepository userRepository;

    @Transactional(transactionManager = "transactionManager")
    public void updateSearchCriteria(Long userId, SearchCriteriaRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        user.setPreferredAgeMin(request.preferredAgeMin());
        user.setPreferredAgeMax(request.preferredAgeMax());
        user.setPreferredGender(request.preferredGender());
        user.setPreferredUniversityId(request.preferredUniversityId());

        userRepository.save(user);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public SearchCriteriaResponse getSearchCriteria(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        return new SearchCriteriaResponse(
                user.getPreferredAgeMin(),
                user.getPreferredAgeMax(),
                user.getPreferredGender(),
                user.getPreferredUniversityId()
        );
    }
}
