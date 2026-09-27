package com.join.back.service;

import com.join.back.model.dto.OutingResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.GroupChat;
import com.join.back.model.entity.GroupMember;
import com.join.back.model.entity.OutingConfirmation;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.GroupChatRepository;
import com.join.back.repository.GroupMemberRepository;
import com.join.back.repository.OutingConfirmationRepository;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Мои походы": upcoming events the user already has company for — one card per
 * match chat and per group, sorted by date.
 */
@Service
@RequiredArgsConstructor
public class OutingService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final ChatRepository chatRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupChatRepository groupChatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OutingConfirmationRepository outingConfirmationRepository;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<OutingResponse> getUpcoming(Long userId) {
        LocalDate today = LocalDate.now(ZONE);

        List<Chat> chats = chatRepository.findByUserId(userId).stream()
                .filter(chat -> (chat.getUser1Id().equals(userId) ? chat.getUser1DeletedAt() : chat.getUser2DeletedAt()) == null)
                .toList();
        List<GroupMember> memberships = groupMemberRepository.findActiveByUserId(userId, Pageable.unpaged()).getContent();
        List<GroupChat> groupChats = memberships.isEmpty()
                ? List.of()
                : groupChatRepository.findByGroupIdIn(memberships.stream().map(GroupMember::getGroupId).toList());

        Set<Long> eventIds = new HashSet<>();
        chats.forEach(chat -> eventIds.add(chat.getEventId()));
        groupChats.forEach(gc -> eventIds.add(gc.getEventId()));
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Event> events = eventRepository.findAllById(eventIds).stream()
                .filter(event -> event.getEventDate() != null && !event.getEventDate().isBefore(today))
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        // Pairs where both confirmed the plan.
        Map<Long, Long> agreedPeople = outingConfirmationRepository
                .findByChatIdIn(chats.stream().map(Chat::getId).toList()).stream()
                .filter(c -> c.getAgreedAt() != null)
                .collect(Collectors.groupingBy(OutingConfirmation::getChatId, Collectors.counting()));

        List<OutingResponse> outings = new ArrayList<>();
        for (Chat chat : chats) {
            Event event = events.get(chat.getEventId());
            if (event == null) continue;
            Long companionId = chat.getUser1Id().equals(userId) ? chat.getUser2Id() : chat.getUser1Id();
            User companion = userRepository.findById(companionId).orElse(null);
            outings.add(toResponse(event, List.of(toCompanion(companionId, companion)), chat.getId(), null,
                    agreedPeople.getOrDefault(chat.getId(), 0L) >= 2));
        }
        for (GroupChat groupChat : groupChats) {
            Event event = events.get(groupChat.getEventId());
            if (event == null) continue;
            List<OutingResponse.Companion> companions = groupMemberRepository
                    .findActiveByGroupIdOrderByJoinedAt(groupChat.getGroupId()).stream()
                    .filter(member -> !member.getUserId().equals(userId))
                    .map(member -> toCompanion(member.getUserId(), userRepository.findById(member.getUserId()).orElse(null)))
                    .toList();
            outings.add(toResponse(event, companions, null, groupChat.getId(), false));
        }
        outings.sort(Comparator.comparing(OutingResponse::eventDate)
                .thenComparing(OutingResponse::eventTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder())));
        return outings;
    }

    private static OutingResponse toResponse(Event event, List<OutingResponse.Companion> companions, Long chatId,
                                             Long groupChatId, boolean agreed) {
        return new OutingResponse(event.getId(), event.getTitle(), event.getImageUrl(), event.getEventDate(),
                event.getEventTime(), event.getCity(), event.isPushkinCard(), companions, chatId, groupChatId, agreed);
    }

    private static OutingResponse.Companion toCompanion(Long userId, User user) {
        return new OutingResponse.Companion(userId, user != null ? user.getFirstName() : null, user != null ? user.getPhoto() : null);
    }
}
