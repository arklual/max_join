package com.join.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.config.IceBreakerProperties;
import com.join.back.model.dto.IceBreakerResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.ChatIceBreaker;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.GroupChat;
import com.join.back.model.entity.GroupChatIceBreaker;
import com.join.back.model.entity.GroupMember;
import com.join.back.model.entity.GroupMemberStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatIceBreakerRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.GroupChatIceBreakerRepository;
import com.join.back.repository.GroupChatRepository;
import com.join.back.repository.GroupMemberRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IceBreakerService {

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<EventType, String> EVENT_TYPE_LABELS = Map.of(
            EventType.CAREER, "Карьерные",
            EventType.THEATER, "Театр",
            EventType.ART, "Искусство",
            EventType.MUSIC, "Музыка",
            EventType.SPORT, "Спорт",
            EventType.CINEMA, "Кино",
            EventType.MASTER_CLASS, "Мастер-класс",
            EventType.EXCURSION, "Экскурсия",
            EventType.FESTIVAL, "Фестиваль"
    );

    private final ChatRepository chatRepository;
    private final ChatIceBreakerRepository iceBreakerRepository;
    private final GroupChatRepository groupChatRepository;
    private final GroupChatIceBreakerRepository groupIceBreakerRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final IceBreakerProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "transactionManager")
    public IceBreakerResponse getIceBreakers(Long chatId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));

        if (!chat.getUser1Id().equals(currentUserId) && !chat.getUser2Id().equals(currentUserId)) {
            throw new AccessDeniedException("User does not have access to this chat");
        }

        Long companionId = chat.getUser1Id().equals(currentUserId) ? chat.getUser2Id() : chat.getUser1Id();

        User currentUser = userRepository.findById(currentUserId).orElse(null);
        User companion = userRepository.findById(companionId).orElse(null);
        Event event = eventRepository.findById(chat.getEventId()).orElse(null);

        List<String> suggestions = getCachedOrGenerate(chatId, currentUserId, currentUser, companion, event);
        return new IceBreakerResponse(suggestions, buildEventInfo(event));
    }

    @Transactional(transactionManager = "transactionManager")
    public IceBreakerResponse getGroupIceBreakers(Long groupChatId, Long currentUserId) {
        GroupChat groupChat = groupChatRepository.findById(groupChatId)
                .orElseThrow(() -> new EntityNotFoundException("Group chat not found with id: " + groupChatId));

        boolean isActiveMember = groupMemberRepository
                .existsByGroupIdAndUserIdAndStatus(groupChat.getGroupId(), currentUserId, GroupMemberStatus.ACTIVE);
        if (!isActiveMember) {
            throw new AccessDeniedException("User is not an active member of this group");
        }

        Event event = eventRepository.findById(groupChat.getEventId()).orElse(null);

        List<GroupMember> members = groupMemberRepository
                .findActiveByGroupIdOrderByJoinedAt(groupChat.getGroupId());
        List<User> memberUsers = userRepository.findAllById(
                members.stream().map(GroupMember::getUserId).collect(Collectors.toList())
        );

        List<String> suggestions = getCachedOrGenerateGroup(groupChatId, memberUsers, event);
        return new IceBreakerResponse(suggestions, buildEventInfo(event));
    }

    private IceBreakerResponse.EventInfoDto buildEventInfo(Event event) {
        if (event == null) return null;
        return new IceBreakerResponse.EventInfoDto(
                event.getId(),
                event.getTitle(),
                event.getEventDate() != null ? event.getEventDate().format(DATE_FMT) : null,
                event.getEventTime() != null ? event.getEventTime().format(TIME_FMT) : null,
                event.getPrice() == null ? null // unknown — not "free"
                        : event.getPrice().signum() == 0 ? "Бесплатно"
                        : event.getPrice().stripTrailingZeros().toPlainString() + " ₽",
                event.getTicketUrl()
        );
    }

    private List<String> getCachedOrGenerateGroup(Long groupChatId, List<User> members, Event event) {
        Optional<GroupChatIceBreaker> cached = groupIceBreakerRepository.findByGroupChatId(groupChatId);
        if (cached.isPresent()) {
            return parseSuggestions(cached.get().getSuggestions());
        }

        List<String> suggestions = generateGroupSuggestions(members, event);

        try {
            String suggestionsJson = objectMapper.writeValueAsString(suggestions);
            GroupChatIceBreaker iceBreaker = GroupChatIceBreaker.builder()
                    .groupChatId(groupChatId)
                    .suggestions(suggestionsJson)
                    .createdAt(LocalDateTime.now())
                    .build();
            groupIceBreakerRepository.save(iceBreaker);
        } catch (Exception e) {
            log.warn("Failed to cache icebreaker suggestions for group chat {}", groupChatId, e);
        }

        return suggestions;
    }

    private List<String> generateGroupSuggestions(List<User> members, Event event) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return generateGroupFallbackSuggestions(members, event);
        }

        try {
            return callGroupAiApi(members, event);
        } catch (Exception e) {
            log.warn("AI group icebreaker generation failed, falling back to templates", e);
            return generateGroupFallbackSuggestions(members, event);
        }
    }

    private List<String> callGroupAiApi(List<User> members, Event event) {
        String prompt = buildGroupPrompt(members, event);
        String systemInstruction = "Ты помощник в приложении для знакомств на мероприятиях. " +
                "Генерируй дружелюбные, лёгкие и интересные фразы для начала группового разговора. " +
                "Отвечай ТОЛЬКО JSON-массивом строк, без markdown и пояснений. " +
                "Пример: [\"Привет всем! ...\", \"Кто уже был на ...\", \"Давайте обсудим ...\"]";
        return callChatCompletionsApi(systemInstruction, prompt);
    }

    private String buildGroupPrompt(List<User> members, Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Сгенерируй ").append(properties.getSuggestionsCount())
                .append(" персонализированных icebreaker-сообщений на русском языке для группового чата.\n\n");

        // Only interests and the event go to the external LLM: no names, ages, universities or bios.
        sb.append("Участники группы (").append(members.size()).append(" чел.):\n");
        for (int i = 0; i < members.size(); i++) {
            sb.append("- Участник ").append(i + 1);
            appendInterests(sb, members.get(i).getInterests());
            sb.append("\n");
        }

        Set<EventType> common = findCommonInterests(members);
        if (!common.isEmpty()) {
            sb.append("Общие интересы: ").append(
                    common.stream().map(EVENT_TYPE_LABELS::get).collect(Collectors.joining(", "))
            ).append("\n");
        }

        appendEventToPrompt(sb, event);

        sb.append("\n\nТребования:\n");
        sb.append("- Обращайся ко всей группе (на «вы» или «ребята»)\n");
        sb.append("- Упоминай мероприятие или общие интересы\n");
        sb.append("- Каждое сообщение — 1-2 предложения, дружелюбно и непринуждённо\n");
        sb.append("- Заканчивай вопросом для продолжения группового обсуждения\n");

        return sb.toString();
    }

    private List<String> generateGroupFallbackSuggestions(List<User> members, Event event) {
        List<String> suggestions = new ArrayList<>();

        if (event != null) {
            suggestions.add(String.format("Привет всем! Кто уже бывал на «%s»? Делитесь впечатлениями!", event.getTitle()));

            if (event.getType() != null) {
                String typeLabel = EVENT_TYPE_LABELS.getOrDefault(event.getType(), "");
                suggestions.add(String.format("Привет, ребята! Вижу, мы все интересуемся направлением «%s». Часто ходите на такие мероприятия?", typeLabel));
            }
        }

        Set<EventType> common = findCommonInterests(members);
        if (!common.isEmpty()) {
            String interest = EVENT_TYPE_LABELS.get(common.iterator().next());
            suggestions.add(String.format("Привет! Заметил(а), что нас всех объединяет %s. Какое последнее мероприятие вам запомнилось?",
                    interest.toLowerCase()));
        }

        if (suggestions.size() < 3) {
            suggestions.add("Привет всем! Рад(а) что мы собрались вместе. Расскажите немного о себе?");
        }
        if (suggestions.size() < 3) {
            suggestions.add("Привет! Как обычно узнаёте о мероприятиях — через друзей или сами ищете?");
        }
        if (suggestions.size() < 3) {
            suggestions.add("Привет, ребята! Какое мероприятие за последнее время впечатлило вас больше всего?");
        }

        return suggestions.subList(0, Math.min(suggestions.size(), 3));
    }

    /**
     * Suggestions are written from the requester to their companion (they use the
     * companion's name), so they are cached per participant, not per chat.
     */
    private List<String> getCachedOrGenerate(Long chatId, Long currentUserId, User currentUser, User companion, Event event) {
        Optional<ChatIceBreaker> cached = iceBreakerRepository.findByChatIdAndUserId(chatId, currentUserId);
        if (cached.isPresent()) {
            return parseSuggestions(cached.get().getSuggestions());
        }

        List<String> suggestions = generateSuggestions(currentUser, companion, event);

        try {
            String suggestionsJson = objectMapper.writeValueAsString(suggestions);
            ChatIceBreaker iceBreaker = ChatIceBreaker.builder()
                    .chatId(chatId)
                    .userId(currentUserId)
                    .suggestions(suggestionsJson)
                    .createdAt(LocalDateTime.now())
                    .build();
            iceBreakerRepository.save(iceBreaker);
        } catch (Exception e) {
            log.warn("Failed to cache icebreaker suggestions for chat {} / user {}", chatId, currentUserId, e);
        }

        return suggestions;
    }

    private List<String> generateSuggestions(User currentUser, User companion, Event event) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return generateFallbackSuggestions(currentUser, companion, event);
        }

        try {
            return callAiApi(currentUser, companion, event);
        } catch (Exception e) {
            log.warn("AI icebreaker generation failed, falling back to templates", e);
            return generateFallbackSuggestions(currentUser, companion, event);
        }
    }

    private List<String> callAiApi(User currentUser, User companion, Event event) {
        String prompt = buildPrompt(currentUser, companion, event);
        String systemInstruction = "Ты помощник в приложении для знакомств на мероприятиях. " +
                "Генерируй дружелюбные, лёгкие и интересные фразы для начала разговора. " +
                "Отвечай ТОЛЬКО JSON-массивом строк, без markdown и пояснений. " +
                "Пример: [\"Привет! ...\", \"Как тебе ...\", \"Слышал, что ...\"]";
        return callChatCompletionsApi(systemInstruction, prompt);
    }

    private List<String> callChatCompletionsApi(String systemInstruction, String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemInstruction),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.8,
                // gpt-6 models spend part of the budget on reasoning tokens
                "max_tokens", 1000
        );

        String responseBody = WebClient.builder().build().post()
                .uri(properties.getBaseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(AI_TIMEOUT);

        return parseAiResponse(responseBody);
    }

    private String buildPrompt(User currentUser, User companion, Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Сгенерируй ").append(properties.getSuggestionsCount())
                .append(" персонализированных icebreaker-сообщений на русском языке.\n\n");

        // Only interests and the event go to the external LLM: no names, ages, universities or bios.
        if (currentUser != null) {
            sb.append("Пользователь 1 (отправитель)");
            appendInterests(sb, currentUser.getInterests());
            sb.append("\n");
        }

        if (companion != null) {
            sb.append("Пользователь 2 (получатель)");
            appendInterests(sb, companion.getInterests());
            sb.append("\n");
        }

        if (currentUser != null && companion != null) {
            Set<EventType> common = currentUser.getInterests().stream()
                    .filter(companion.getInterests()::contains)
                    .collect(Collectors.toSet());
            if (!common.isEmpty()) {
                sb.append("Общие интересы: ").append(
                        common.stream().map(EVENT_TYPE_LABELS::get).collect(Collectors.joining(", "))
                ).append("\n");
            }
        }

        appendEventToPrompt(sb, event);

        sb.append("\n\nТребования:\n");
        sb.append("- Обращайся к получателю на «ты», без имени\n");
        sb.append("- Упоминай мероприятие или общие интересы\n");
        sb.append("- Каждое сообщение — 1-2 предложения, дружелюбно и непринуждённо\n");
        sb.append("- Заканчивай вопросом для продолжения диалога\n");

        return sb.toString();
    }

    private Set<EventType> findCommonInterests(List<User> members) {
        if (members.size() < 2 || members.get(0).getInterests() == null) {
            return Set.of();
        }
        Set<EventType> common = new HashSet<>(members.get(0).getInterests());
        for (int i = 1; i < members.size(); i++) {
            if (members.get(i).getInterests() != null) {
                common.retainAll(members.get(i).getInterests());
            }
        }
        return common;
    }

    private void appendEventToPrompt(StringBuilder sb, Event event) {
        if (event == null) return;
        sb.append("\nМероприятие: ").append(event.getTitle());
        sb.append(" (").append(EVENT_TYPE_LABELS.getOrDefault(event.getType(), event.getType().name())).append(")");
        if (event.getEventDate() != null) sb.append(", ").append(event.getEventDate().format(DATE_FMT));
        if (event.getDescription() != null) {
            String desc = event.getDescription().length() > 200
                    ? event.getDescription().substring(0, 200) + "..."
                    : event.getDescription();
            sb.append(". Описание: ").append(desc);
        }
    }

    private void appendInterests(StringBuilder sb, List<EventType> interests) {
        if (interests != null && !interests.isEmpty()) {
            sb.append(". Интересы: ").append(
                    interests.stream().map(EVENT_TYPE_LABELS::get).collect(Collectors.joining(", "))
            );
        }
    }

    private List<String> parseAiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0)
                    .path("message").path("content").asText();

            // Strip possible markdown code fences
            content = content.strip();
            if (content.startsWith("```")) {
                content = content.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "").strip();
            }

            JsonNode array = objectMapper.readTree(content);
            List<String> result = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode node : array) {
                    result.add(node.asText());
                }
            }
            return result.isEmpty() ? generateFallbackSuggestions(null, null, null) : result;
        } catch (Exception e) {
            log.warn("Failed to parse AI response", e);
            return generateFallbackSuggestions(null, null, null);
        }
    }

    private List<String> generateFallbackSuggestions(User currentUser, User companion, Event event) {
        List<String> suggestions = new ArrayList<>();
        String companionName = companion != null && companion.getFirstName() != null
                ? companion.getFirstName() : "";

        if (event != null) {
            suggestions.add(String.format("Привет%s! Тоже собираешься на «%s»? Как узнал(а) о нём?",
                    companionName.isEmpty() ? "" : ", " + companionName, event.getTitle()));

            if (event.getType() != null) {
                String typeLabel = EVENT_TYPE_LABELS.getOrDefault(event.getType(), "");
                suggestions.add(String.format("Привет! Вижу, мы оба интересуемся направлением «%s». Часто ходишь на такие мероприятия?", typeLabel));
            }
        }

        if (currentUser != null && companion != null) {
            Set<EventType> common = currentUser.getInterests().stream()
                    .filter(companion.getInterests()::contains)
                    .collect(Collectors.toSet());
            if (!common.isEmpty()) {
                String interest = EVENT_TYPE_LABELS.get(common.iterator().next());
                suggestions.add(String.format("Привет%s! Заметил(а), что мы оба любим %s. Какое последнее мероприятие тебе запомнилось?",
                        companionName.isEmpty() ? "" : ", " + companionName, interest.toLowerCase()));
            }

            if (companion.getUniversity() != null && currentUser.getUniversity() != null
                    && companion.getUniversity().getId().equals(currentUser.getUniversity().getId())) {
                suggestions.add(String.format("О, мы из одного универа — %s! На каком ты факультете?",
                        companion.getUniversity().getName()));
            }
        }

        // Generic fallbacks if not enough
        if (suggestions.size() < 3) {
            suggestions.add(String.format("Привет%s! Рад(а) что мы совпали. Расскажи немного о себе?",
                    companionName.isEmpty() ? "" : ", " + companionName));
        }
        if (suggestions.size() < 3) {
            suggestions.add("Привет! С кем обычно ходишь на мероприятия — с друзьями или любишь открывать новое в одиночку?");
        }
        if (suggestions.size() < 3) {
            suggestions.add("Привет! Какое мероприятие за последнее время впечатлило тебя больше всего?");
        }

        return suggestions.subList(0, Math.min(suggestions.size(), 3));
    }

    private List<String> parseSuggestions(String json) {
        try {
            JsonNode array = objectMapper.readTree(json);
            List<String> result = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode node : array) {
                    result.add(node.asText());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse cached suggestions", e);
            return List.of("Привет! Рад(а) что мы совпали. Расскажи немного о себе?");
        }
    }
}
