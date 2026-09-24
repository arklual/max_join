package com.join.back.web.controller;

import com.join.back.model.entity.Chat;
import com.join.back.model.entity.ChatMessage;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventLike;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.GroupChat;
import com.join.back.model.entity.GroupGathering;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.SupportTicket;
import com.join.back.model.entity.University;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatIceBreakerRepository;
import com.join.back.repository.ChatMessageRepository;
import com.join.back.repository.GroupChatIceBreakerRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.GroupChatMessageRepository;
import com.join.back.repository.GroupChatRepository;
import com.join.back.repository.GroupGatheringRepository;
import com.join.back.repository.GroupMemberRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.NotificationRepository;
import com.join.back.repository.SupportMessageRepository;
import com.join.back.repository.SupportTicketRepository;
import com.join.back.repository.UniversityRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final EventLikeRepository eventLikeRepository;
    private final MatchRepository matchRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatIceBreakerRepository chatIceBreakerRepository;
    private final NotificationRepository notificationRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final GroupGatheringRepository groupGatheringRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupChatRepository groupChatRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final GroupChatIceBreakerRepository groupChatIceBreakerRepository;

    @GetMapping("/events")
    public List<AdminEventResponse> getEvents() {
        return eventRepository.findAll().stream()
                .map(this::toEventResponse)
                .toList();
    }

    @GetMapping("/events/{id}")
    public AdminEventResponse getEvent(@PathVariable Long id) {
        return toEventResponse(findEvent(id));
    }

    @PostMapping("/events")
    public ResponseEntity<AdminEventResponse> createEvent(@RequestBody AdminEventPayload payload) {
        Event saved = eventRepository.save(toEventEntity(new Event(), payload, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(toEventResponse(saved));
    }

    @PutMapping("/events/{id}")
    public AdminEventResponse updateEvent(@PathVariable Long id, @RequestBody AdminEventPayload payload) {
        Event event = findEvent(id);
        return toEventResponse(eventRepository.save(toEventEntity(event, payload, false)));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventRepository.delete(findEvent(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public List<AdminUserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .toList();
    }

    @GetMapping("/users/{id}")
    public AdminUserResponse getUser(@PathVariable Long id) {
        return toUserResponse(findUser(id));
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@RequestBody AdminUserPayload payload) {
        User saved = userRepository.save(toUserEntity(new User(), payload, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserResponse(saved));
    }

    @PutMapping("/users/{id}")
    public AdminUserResponse updateUser(@PathVariable Long id, @RequestBody AdminUserPayload payload) {
        User user = findUser(id);
        return toUserResponse(userRepository.save(toUserEntity(user, payload, false)));
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User user = findUser(id);

        // 1. Delete group data for groups created by this user
        List<GroupGathering> createdGroups = groupGatheringRepository.findByCreatorId(id);
        if (!createdGroups.isEmpty()) {
            List<Long> groupIds = createdGroups.stream().map(GroupGathering::getId).toList();
            List<GroupChat> groupChats = groupChatRepository.findByGroupIdIn(groupIds);
            if (!groupChats.isEmpty()) {
                List<Long> groupChatIds = groupChats.stream().map(GroupChat::getId).toList();
                groupChatMessageRepository.deleteByGroupChatIdIn(groupChatIds);
                groupChatIceBreakerRepository.deleteByGroupChatIdIn(groupChatIds);
            }
            groupChatRepository.deleteByGroupIdIn(groupIds);
            groupMemberRepository.deleteByGroupIdIn(groupIds);
            groupGatheringRepository.deleteAll(createdGroups);
        }

        // 2. Delete group messages sent by user and group memberships
        groupChatMessageRepository.deleteBySenderId(id);
        groupMemberRepository.deleteByUserId(id);

        // 3. Delete chats and related data (messages, icebreakers)
        List<Chat> userChats = chatRepository.findByUserId(id);
        if (!userChats.isEmpty()) {
            List<Long> chatIds = userChats.stream().map(Chat::getId).toList();
            chatMessageRepository.deleteByChatIdIn(chatIds);
            chatIceBreakerRepository.deleteByChatIdIn(chatIds);
            chatRepository.deleteAll(userChats);
        }

        // 4. Delete matches
        matchRepository.deleteByUserId(id);

        // 5. Delete event likes
        eventLikeRepository.deleteByUserId(id);

        // 6. Delete notifications
        notificationRepository.deleteByUserId(id);

        // 7. Delete support ticket and messages
        supportTicketRepository.findByUserId(id).ifPresent(ticket -> {
            supportMessageRepository.deleteByTicketId(ticket.getId());
            supportTicketRepository.delete(ticket);
        });

        // 8. Delete user (user_interests cleaned up by JPA)
        userRepository.delete(user);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/event-likes")
    public List<AdminEventLikeResponse> getEventLikes() {
        return eventLikeRepository.findAll().stream()
                .map(this::toEventLikeResponse)
                .toList();
    }

    @GetMapping("/event-likes/{id}")
    public AdminEventLikeResponse getEventLike(@PathVariable Long id) {
        return toEventLikeResponse(findEventLike(id));
    }

    @PostMapping("/event-likes")
    public ResponseEntity<AdminEventLikeResponse> createEventLike(@RequestBody AdminEventLikePayload payload) {
        EventLike saved = eventLikeRepository.save(toEventLikeEntity(new EventLike(), payload, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(toEventLikeResponse(saved));
    }

    @PutMapping("/event-likes/{id}")
    public AdminEventLikeResponse updateEventLike(@PathVariable Long id, @RequestBody AdminEventLikePayload payload) {
        EventLike eventLike = findEventLike(id);
        return toEventLikeResponse(eventLikeRepository.save(toEventLikeEntity(eventLike, payload, false)));
    }

    @DeleteMapping("/event-likes/{id}")
    public ResponseEntity<Void> deleteEventLike(@PathVariable Long id) {
        eventLikeRepository.delete(findEventLike(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/matches")
    public List<AdminMatchResponse> getMatches() {
        return matchRepository.findAll().stream()
                .map(this::toMatchResponse)
                .toList();
    }

    @GetMapping("/matches/{id}")
    public AdminMatchResponse getMatch(@PathVariable Long id) {
        return toMatchResponse(findMatch(id));
    }

    @PostMapping("/matches")
    public ResponseEntity<AdminMatchResponse> createMatch(@RequestBody AdminMatchPayload payload) {
        Match saved = matchRepository.save(toMatchEntity(new Match(), payload, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(toMatchResponse(saved));
    }

    @PutMapping("/matches/{id}")
    public AdminMatchResponse updateMatch(@PathVariable Long id, @RequestBody AdminMatchPayload payload) {
        Match match = findMatch(id);
        return toMatchResponse(matchRepository.save(toMatchEntity(match, payload, false)));
    }

    @DeleteMapping("/matches/{id}")
    public ResponseEntity<Void> deleteMatch(@PathVariable Long id) {
        matchRepository.delete(findMatch(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chats")
    public List<AdminChatResponse> getChats() {
        return chatRepository.findAll().stream()
                .map(this::toChatResponse)
                .toList();
    }

    @GetMapping("/chats/{id}")
    public AdminChatResponse getChat(@PathVariable Long id) {
        return toChatResponse(findChat(id));
    }

    @PostMapping("/chats")
    public ResponseEntity<AdminChatResponse> createChat(@RequestBody AdminChatPayload payload) {
        Chat saved = chatRepository.save(toChatEntity(new Chat(), payload, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(toChatResponse(saved));
    }

    @PutMapping("/chats/{id}")
    public AdminChatResponse updateChat(@PathVariable Long id, @RequestBody AdminChatPayload payload) {
        Chat chat = findChat(id);
        return toChatResponse(chatRepository.save(toChatEntity(chat, payload, false)));
    }

    @DeleteMapping("/chats/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long id) {
        chatRepository.delete(findChat(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chat-messages")
    public List<AdminChatMessageResponse> getChatMessages() {
        return chatMessageRepository.findAll().stream()
                .map(this::toChatMessageResponse)
                .toList();
    }

    @GetMapping("/chat-messages/{id}")
    public AdminChatMessageResponse getChatMessage(@PathVariable Long id) {
        return toChatMessageResponse(findChatMessage(id));
    }

    @PostMapping("/chat-messages")
    public ResponseEntity<AdminChatMessageResponse> createChatMessage(@RequestBody AdminChatMessagePayload payload) {
        ChatMessage saved = chatMessageRepository.save(toChatMessageEntity(new ChatMessage(), payload, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(toChatMessageResponse(saved));
    }

    @PutMapping("/chat-messages/{id}")
    public AdminChatMessageResponse updateChatMessage(@PathVariable Long id, @RequestBody AdminChatMessagePayload payload) {
        ChatMessage chatMessage = findChatMessage(id);
        return toChatMessageResponse(chatMessageRepository.save(toChatMessageEntity(chatMessage, payload, false)));
    }

    @DeleteMapping("/chat-messages/{id}")
    public ResponseEntity<Void> deleteChatMessage(@PathVariable Long id) {
        chatMessageRepository.delete(findChatMessage(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/universities")
    public List<AdminUniversityResponse> getUniversities() {
        return universityRepository.findAll().stream()
                .map(this::toUniversityResponse)
                .toList();
    }

    @GetMapping("/universities/{id}")
    public AdminUniversityResponse getUniversity(@PathVariable Long id) {
        return toUniversityResponse(findUniversity(id));
    }

    @PostMapping("/universities")
    public ResponseEntity<AdminUniversityResponse> createUniversity(@RequestBody AdminUniversityPayload payload) {
        University university = new University();
        university.setName(required(payload.name(), "name"));
        university.setCity(required(payload.city(), "city"));
        University saved = universityRepository.save(university);
        return ResponseEntity.status(HttpStatus.CREATED).body(toUniversityResponse(saved));
    }

    @PutMapping("/universities/{id}")
    public AdminUniversityResponse updateUniversity(@PathVariable Long id, @RequestBody AdminUniversityPayload payload) {
        University university = findUniversity(id);
        university.setName(required(payload.name(), "name"));
        university.setCity(required(payload.city(), "city"));
        return toUniversityResponse(universityRepository.save(university));
    }

    @DeleteMapping("/universities/{id}")
    public ResponseEntity<Void> deleteUniversity(@PathVariable Long id) {
        universityRepository.delete(findUniversity(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metadata")
    public AdminMetadataResponse getMetadata() {
        return new AdminMetadataResponse(
                enumNames(EventType.values()),
                enumNames(Gender.values())
        );
    }

    private Event findEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + id));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private EventLike findEventLike(Long id) {
        return eventLikeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event like not found: " + id));
    }

    private Match findMatch(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match not found: " + id));
    }

    private Chat findChat(Long id) {
        return chatRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found: " + id));
    }

    private ChatMessage findChatMessage(Long id) {
        return chatMessageRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Chat message not found: " + id));
    }

    private University findUniversity(Long id) {
        return universityRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("University not found: " + id));
    }

    private Event toEventEntity(Event event, AdminEventPayload payload, boolean create) {
        event.setTitle(required(payload.title(), "title"));
        event.setDescription(payload.description());
        event.setType(parseEventType(payload.type()));
        event.setImageUrl(payload.imageUrl());
        event.setPrice(payload.price());
        event.setEventDate(required(payload.eventDate(), "eventDate"));
        event.setEventTime(payload.eventTime());
        event.setTicketUrl(payload.ticketUrl());
        event.setCity(required(payload.city(), "city"));
        if (create || payload.createdAt() != null) {
            event.setCreatedAt(defaultCreatedAt(payload.createdAt(), create ? LocalDateTime.now() : event.getCreatedAt()));
        }
        return event;
    }

    private User toUserEntity(User user, AdminUserPayload payload, boolean create) {
        user.setMaxId(required(payload.maxId(), "maxId"));
        user.setEmail(payload.email());
        user.setCity(payload.city());
        user.setFirstName(payload.firstName());
        user.setLastName(payload.lastName());
        user.setGender(parseGender(payload.gender()));
        user.setAge(payload.age());
        user.setPhoto(payload.photo());
        user.setTelegramChannel(payload.telegramChannel());
        user.setStatus(payload.status());
        user.setBio(payload.bio());
        user.setPreferredAgeMin(payload.preferredAgeMin());
        user.setPreferredAgeMax(payload.preferredAgeMax());
        user.setPreferredGender(parseGender(payload.preferredGender()));
        user.setPreferredUniversityId(payload.preferredUniversityId());
        user.setInterests(parseEventTypes(payload.interests()));
        if (payload.universityId() != null) {
            user.setUniversity(universityRepository.findById(payload.universityId())
                    .orElseThrow(() -> new EntityNotFoundException("University not found: " + payload.universityId())));
        } else {
            user.setUniversity(null);
        }
        if (create || payload.createdAt() != null) {
            user.setCreatedAt(defaultCreatedAt(payload.createdAt(), create ? LocalDateTime.now() : user.getCreatedAt()));
        }
        return user;
    }

    private EventLike toEventLikeEntity(EventLike eventLike, AdminEventLikePayload payload, boolean create) {
        eventLike.setUserId(required(payload.userId(), "userId"));
        eventLike.setEventId(required(payload.eventId(), "eventId"));
        if (create || payload.createdAt() != null) {
            eventLike.setCreatedAt(defaultCreatedAt(payload.createdAt(), create ? LocalDateTime.now() : eventLike.getCreatedAt()));
        }
        return eventLike;
    }

    private Match toMatchEntity(Match match, AdminMatchPayload payload, boolean create) {
        match.setUser1Id(required(payload.user1Id(), "user1Id"));
        match.setUser2Id(required(payload.user2Id(), "user2Id"));
        match.setEventId(required(payload.eventId(), "eventId"));
        if (create || payload.createdAt() != null) {
            match.setCreatedAt(defaultCreatedAt(payload.createdAt(), create ? LocalDateTime.now() : match.getCreatedAt()));
        }
        return match;
    }

    private Chat toChatEntity(Chat chat, AdminChatPayload payload, boolean create) {
        chat.setUser1Id(required(payload.user1Id(), "user1Id"));
        chat.setUser2Id(required(payload.user2Id(), "user2Id"));
        chat.setEventId(required(payload.eventId(), "eventId"));
        chat.setMatchId(required(payload.matchId(), "matchId"));
        if (create || payload.createdAt() != null) {
            chat.setCreatedAt(defaultCreatedAt(payload.createdAt(), create ? LocalDateTime.now() : chat.getCreatedAt()));
        }
        return chat;
    }

    private ChatMessage toChatMessageEntity(ChatMessage chatMessage, AdminChatMessagePayload payload, boolean create) {
        chatMessage.setChatId(required(payload.chatId(), "chatId"));
        chatMessage.setSenderId(required(payload.senderId(), "senderId"));
        chatMessage.setText(required(payload.text(), "text"));
        chatMessage.setRead(payload.isRead());
        if (create || payload.createdAt() != null) {
            chatMessage.setCreatedAt(defaultCreatedAt(payload.createdAt(), create ? LocalDateTime.now() : chatMessage.getCreatedAt()));
        }
        return chatMessage;
    }

    private AdminEventResponse toEventResponse(Event event) {
        return new AdminEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getType().name(),
                event.getImageUrl(),
                event.getPrice(),
                event.getEventDate(),
                event.getEventTime(),
                event.getTicketUrl(),
                event.getCity(),
                event.getCreatedAt()
        );
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getMaxId(),
                user.getEmail(),
                user.getCity(),
                user.getFirstName(),
                user.getLastName(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getAge(),
                user.getPhoto(),
                user.getTelegramChannel(),
                user.getStatus(),
                user.getBio(),
                user.getPreferredAgeMin(),
                user.getPreferredAgeMax(),
                user.getPreferredGender() != null ? user.getPreferredGender().name() : null,
                user.getPreferredUniversityId(),
                user.getInterests().stream().map(Enum::name).toList(),
                user.getUniversity() != null ? user.getUniversity().getId() : null,
                user.getUniversity() != null ? user.getUniversity().getName() : null,
                user.getCreatedAt()
        );
    }

    private AdminEventLikeResponse toEventLikeResponse(EventLike eventLike) {
        return new AdminEventLikeResponse(
                eventLike.getId(),
                eventLike.getUserId(),
                eventLike.getEventId(),
                eventLike.getCreatedAt()
        );
    }

    private AdminMatchResponse toMatchResponse(Match match) {
        return new AdminMatchResponse(
                match.getId(),
                match.getUser1Id(),
                match.getUser2Id(),
                match.getEventId(),
                match.getCreatedAt()
        );
    }

    private AdminChatResponse toChatResponse(Chat chat) {
        return new AdminChatResponse(
                chat.getId(),
                chat.getUser1Id(),
                chat.getUser2Id(),
                chat.getEventId(),
                chat.getMatchId(),
                chat.getCreatedAt()
        );
    }

    private AdminChatMessageResponse toChatMessageResponse(ChatMessage chatMessage) {
        return new AdminChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getChatId(),
                chatMessage.getSenderId(),
                chatMessage.getText(),
                chatMessage.getCreatedAt(),
                chatMessage.isRead()
        );
    }

    private EventType parseEventType(String rawValue) {
        return EventType.valueOf(required(rawValue, "type").trim().toUpperCase(Locale.ROOT));
    }

    private Gender parseGender(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Gender.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
    }

    private List<EventType> parseEventTypes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> EventType.valueOf(value.trim().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    private LocalDateTime defaultCreatedAt(LocalDateTime provided, LocalDateTime fallback) {
        return provided != null ? provided : fallback;
    }

    private List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .toList();
    }

    public record AdminEventPayload(
            String title,
            String description,
            String type,
            String imageUrl,
            BigDecimal price,
            LocalDate eventDate,
            LocalTime eventTime,
            String ticketUrl,
            String city,
            LocalDateTime createdAt
    ) {
    }

    public record AdminEventResponse(
            Long id,
            String title,
            String description,
            String type,
            String imageUrl,
            BigDecimal price,
            LocalDate eventDate,
            LocalTime eventTime,
            String ticketUrl,
            String city,
            LocalDateTime createdAt
    ) {
    }

    public record AdminUserPayload(
            Long maxId,
            String email,
            String city,
            String firstName,
            String lastName,
            String gender,
            Integer age,
            String photo,
            String telegramChannel,
            String status,
            String bio,
            Integer preferredAgeMin,
            Integer preferredAgeMax,
            String preferredGender,
            Long preferredUniversityId,
            List<String> interests,
            Long universityId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminUserResponse(
            Long id,
            Long maxId,
            String email,
            String city,
            String firstName,
            String lastName,
            String gender,
            Integer age,
            String photo,
            String telegramChannel,
            String status,
            String bio,
            Integer preferredAgeMin,
            Integer preferredAgeMax,
            String preferredGender,
            Long preferredUniversityId,
            List<String> interests,
            Long universityId,
            String universityName,
            LocalDateTime createdAt
    ) {
    }

    public record AdminEventLikePayload(
            Long userId,
            Long eventId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminEventLikeResponse(
            Long id,
            Long userId,
            Long eventId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminMatchPayload(
            Long user1Id,
            Long user2Id,
            Long eventId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminMatchResponse(
            Long id,
            Long user1Id,
            Long user2Id,
            Long eventId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminChatPayload(
            Long user1Id,
            Long user2Id,
            Long eventId,
            Long matchId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminChatResponse(
            Long id,
            Long user1Id,
            Long user2Id,
            Long eventId,
            Long matchId,
            LocalDateTime createdAt
    ) {
    }

    public record AdminChatMessagePayload(
            Long chatId,
            Long senderId,
            String text,
            LocalDateTime createdAt,
            boolean isRead
    ) {
    }

    public record AdminChatMessageResponse(
            Long id,
            Long chatId,
            Long senderId,
            String text,
            LocalDateTime createdAt,
            boolean isRead
    ) {
    }

    private AdminUniversityResponse toUniversityResponse(University university) {
        return new AdminUniversityResponse(
                university.getId(),
                university.getName(),
                university.getCity()
        );
    }

    public record AdminUniversityPayload(
            String name,
            String city
    ) {
    }

    public record AdminUniversityResponse(
            Long id,
            String name,
            String city
    ) {
    }

    public record AdminMetadataResponse(
            List<String> eventTypes,
            List<String> genders
    ) {
    }
}
