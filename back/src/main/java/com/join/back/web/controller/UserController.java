package com.join.back.web.controller;

import com.join.back.model.dto.UserProfileResponse;
import com.join.back.model.dto.UserProfileUpdateRequest;
import com.join.back.model.dto.UserRegistrationRequest;
import com.join.back.model.dto.UserResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.AccountDeletionService;
import com.join.back.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseAuthController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    public UserController(UserRepository userRepository, UserService userService,
                          AccountDeletionService accountDeletionService) {
        super(userRepository);
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = ApiDocs.AUTH)
    @Operation(summary = "Регистрация в мини-приложении",
            description = "Пользователь MAX определяется по заголовку `X-Max-Init-Data`, пароль не нужен. Возраст — "
                    + "от 14 лет, `personalDataConsent=true`. Фото — необязательное поле `photo`.")
    @ApiError(code = "400", description = "Младше 14 лет или нет согласия на обработку данных")
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> register(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam("city") String city,
            @RequestParam("name") String firstName,
            @RequestParam("gender") String gender,
            @RequestParam("age") Integer age,
            @RequestParam(value = "interests", required = false) List<String> interests,
            @RequestParam(value = "universityId", required = false) Long universityId,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "personalDataConsent", defaultValue = "false") boolean personalDataConsent) {
        MessengerAuthenticationToken identity = getCurrentMessengerIdentity();
        UserRegistrationRequest request = new UserRegistrationRequest(
                email, city, firstName, gender, age,
                interests != null ? interests : List.of(),
                universityId,
                personalDataConsent
        );
        UserResponse response = userService.register(identity.getMessenger(), identity.getExternalId(), request, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Текущий пользователь",
            description = "Кратко: город, возраст, интересы, вуз и отметка согласия на обработку данных. `404` — "
                    + "пользователь MAX ещё не зарегистрирован в JOIN.")
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
                            user.getUniversity() != null ? user.getUniversity().getName() : null,
                            user.getPersonalDataConsentAt() != null
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Профиль другого пользователя",
            description = "Подростки (14–17 лет) и взрослые не видят профили друг друга — в этом случае `404`.")
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponse> getProfileById(@PathVariable("id") Long userId) {
        UserProfileResponse response = userService.getProfileById(requireCurrentUserId(), userId);
        return ResponseEntity.ok(response);
    }

    /** Consent to the privacy policy for accounts created before it was asked at sign-up. */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Принять политику конфиденциальности",
            description = "Для аккаунтов, созданных до того, как согласие стало обязательным при регистрации. "
                    + "Повторный вызов ничего не меняет.")
    @PostMapping("/me/consent")
    public ResponseEntity<Void> acceptPersonalDataConsent() {
        userService.acceptPersonalDataConsent(requireCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** Deletes the account and everything tied to it — withdraws consent to data processing. */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Удалить аккаунт",
            description = "Удаляет профиль, сохранённые события, совпадения, личные чаты и созданные компании; из "
                    + "групп друзей пользователь выходит. Так же отзывается согласие на обработку данных.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount() {
        accountDeletionService.deleteAccount(requireCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Мой профиль",
            description = "Полный профиль: фото, «о себе», статус, канал, привязан ли пароль.")
    @GetMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        Long userId = requireCurrentUserId();
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Изменить профиль",
            description = "Меняются только переданные поля. Возраст — от 14 лет.")
    @PutMapping("/me/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        Long userId = requireCurrentUserId();
        UserProfileResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @Tag(name = ApiDocs.PROFILE)
    @Operation(summary = "Загрузить фото профиля",
            description = "Файл в поле `photo`, до 10 МБ.")
    @ApiError(code = "413", description = "Файл больше 10 МБ")
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> uploadPhoto(@RequestParam("photo") MultipartFile file) {
        Long userId = requireCurrentUserId();
        UserProfileResponse response = userService.uploadPhoto(userId, file);
        return ResponseEntity.ok(response);
    }
}
