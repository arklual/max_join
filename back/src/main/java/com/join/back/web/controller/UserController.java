package com.join.back.web.controller;

import com.join.back.model.dto.UserProfileResponse;
import com.join.back.model.dto.UserProfileUpdateRequest;
import com.join.back.model.dto.UserRegistrationRequest;
import com.join.back.model.dto.UserResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.join.back.security.MessengerAuthenticationToken;

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseAuthController {

    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        super(userRepository);
        this.userService = userService;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> register(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam("city") String city,
            @RequestParam("name") String firstName,
            @RequestParam("gender") String gender,
            @RequestParam("age") Integer age,
            @RequestParam(value = "interests", required = false) List<String> interests,
            @RequestParam(value = "universityId", required = false) Long universityId,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        MessengerAuthenticationToken identity = getCurrentMessengerIdentity();
        UserRegistrationRequest request = new UserRegistrationRequest(
                email, city, firstName, gender, age,
                interests != null ? interests : List.of(),
                universityId
        );
        UserResponse response = userService.register(identity.getMessenger(), identity.getExternalId(), request, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Long userId = requireCurrentUserId();
        return userRepository.findById(userId)
                .map(user -> {
                    UserResponse response = new UserResponse(
                            user.getId(),
                            user.getMaxId(),
                            user.getEmail(),
                            user.getCity(),
                            user.getFirstName(),
                            user.getGender(),
                            user.getAge(),
                            user.getInterests().stream()
                                    .map(Enum::name)
                                    .toList(),
                            user.getPhoto(),
                            user.getUniversity() != null ? user.getUniversity().getId() : null,
                            user.getUniversity() != null ? user.getUniversity().getName() : null
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponse> getProfileById(@PathVariable("id") Long userId) {
        UserProfileResponse response = userService.getProfileById(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        Long userId = requireCurrentUserId();
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        Long userId = requireCurrentUserId();
        UserProfileResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/photo")
    public ResponseEntity<UserProfileResponse> uploadPhoto(@RequestParam("photo") MultipartFile file) {
        Long userId = requireCurrentUserId();
        UserProfileResponse response = userService.uploadPhoto(userId, file);
        return ResponseEntity.ok(response);
    }
}
