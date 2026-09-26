package com.join.back.service;

import com.join.back.model.dto.GroupInviteCandidateResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.FriendGroupMember;
import com.join.back.model.entity.FriendGroupMemberStatus;
import com.join.back.model.entity.GroupGathering;
import com.join.back.model.entity.GroupMemberStatus;
import com.join.back.model.entity.GroupStatus;
import com.join.back.model.entity.NotificationType;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.FriendGroupMemberRepository;
import com.join.back.repository.GroupGatheringRepository;
import com.join.back.repository.GroupMemberRepository;
import com.join.back.repository.NotificationRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Invites people the user already knows (match chats, friend groups) into an event group:
 * "I'm going with a friend — and whoever else wants to join".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final GroupGatheringRepository groupGatheringRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatRepository chatRepository;
    private final FriendGroupMemberRepository friendGroupMemberRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final MessengerNotificationService messengerNotificationService;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<GroupInviteCandidateResponse> getCandidates(Long userId, Long groupId) {
        GroupGathering group = requireGroupMember(userId, groupId);
        Set<Long> contactIds = contactIds(userId);
        if (contactIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(contactIds).stream()
                .sorted(Comparator.comparing(u -> u.getFirstName() == null ? "" : u.getFirstName()))
                .map(u -> new GroupInviteCandidateResponse(u.getId(), u.getFirstName(), u.getPhoto(),
                        candidateStatus(group, u.getId())))
                .toList();
    }

    @Transactional(transactionManager = "transactionManager")
    public GroupInviteCandidateResponse invite(Long userId, Long groupId, Long inviteeId) {
        GroupGathering group = requireGroupMember(userId, groupId);
        if (group.getStatus() != GroupStatus.OPEN) {
            throw new UserActionException("В компании не осталось свободных мест");
        }
        if (!contactIds(userId).contains(inviteeId)) {
            throw new UserActionException("Позвать можно только тех, с кем вы уже общались в JOIN");
        }
        String status = candidateStatus(group, inviteeId);
        switch (status) {
            case "IN_GROUP" -> throw new UserActionException("Этот человек уже в компании");
            case "BUSY" -> throw new UserActionException("Этот человек уже идёт на событие с другой компанией");
            case "INVITED" -> throw new UserActionException("Приглашение уже отправлено");
            default -> { }
        }

        User inviter = userRepository.findById(userId).orElse(null);
        User invitee = userRepository.findById(inviteeId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + inviteeId));
        Event event = eventRepository.findById(group.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + group.getEventId()));
        String inviterName = inviter != null ? inviter.getFirstName() : null;

        notificationService.createGroupInviteNotification(inviteeId, groupId, inviterName, event.getTitle());
        try {
            messengerNotificationService.sendGroupInvite(invitee, inviterName, event.getTitle(), when(event), groupId);
        } catch (Exception e) {
            log.warn("Failed to send group invite to user {}: {}", inviteeId, e.getMessage());
        }
        return new GroupInviteCandidateResponse(invitee.getId(), invitee.getFirstName(), invitee.getPhoto(), "INVITED");
    }

    private GroupGathering requireGroupMember(Long userId, Long groupId) {
        GroupGathering group = groupGatheringRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + groupId));
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, GroupMemberStatus.ACTIVE)) {
            throw new AccessDeniedException("User is not an active member of this group");
        }
        return group;
    }

    /** Match companions (chats the user hasn't deleted) and members of the user's friend groups. */
    private Set<Long> contactIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Chat chat : chatRepository.findByUserId(userId)) {
            boolean mine1 = chat.getUser1Id().equals(userId);
            if ((mine1 ? chat.getUser1DeletedAt() : chat.getUser2DeletedAt()) == null) {
                ids.add(mine1 ? chat.getUser2Id() : chat.getUser1Id());
            }
        }
        for (FriendGroupMember own : friendGroupMemberRepository
                .findByUserIdAndStatus(userId, FriendGroupMemberStatus.ACTIVE, Pageable.unpaged())) {
            friendGroupMemberRepository
                    .findByFriendGroupIdAndStatusOrderByJoinedAtAsc(own.getFriendGroupId(), FriendGroupMemberStatus.ACTIVE)
                    .forEach(m -> ids.add(m.getUserId()));
        }
        ids.remove(userId);
        ids.removeAll(userBlockService.relatedUserIds(userId));
        return ids;
    }

    private String candidateStatus(GroupGathering group, Long candidateId) {
        if (groupMemberRepository.existsByGroupIdAndUserIdAndStatus(group.getId(), candidateId, GroupMemberStatus.ACTIVE)) {
            return "IN_GROUP";
        }
        if (groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(group.getEventId(), candidateId)) {
            return "BUSY";
        }
        if (notificationRepository.existsByUserIdAndGroupIdAndType(candidateId, group.getId(), NotificationType.GROUP_INVITE)) {
            return "INVITED";
        }
        return "AVAILABLE";
    }

    private static String when(Event event) {
        LocalDate date = event.getEventDate();
        if (date == null) {
            return null;
        }
        String day = date.equals(LocalDate.now()) ? "сегодня"
                : date.equals(LocalDate.now().plusDays(1)) ? "завтра" : date.format(DATE);
        return event.getEventTime() != null ? day + " в " + event.getEventTime().format(TIME) : day;
    }
}
