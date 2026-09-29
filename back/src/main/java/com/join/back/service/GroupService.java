package com.join.back.service;

import com.join.back.model.dto.CreateGroupRequest;
import com.join.back.model.dto.GroupChatMessageResponse;
import com.join.back.model.dto.GroupMemberResponse;
import com.join.back.model.dto.GroupResponse;
import com.join.back.model.dto.GroupStatsResponse;
import com.join.back.model.dto.JoinGroupResponse;
import com.join.back.model.dto.LeaveGroupResponse;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.GroupChat;
import com.join.back.model.entity.GroupChatMessage;
import com.join.back.model.entity.GroupGathering;
import com.join.back.model.entity.GroupMember;
import com.join.back.model.entity.GroupMemberRole;
import com.join.back.model.entity.GroupMemberStatus;
import com.join.back.model.entity.GroupStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.EventRepository;
import com.join.back.repository.GroupChatMessageRepository;
import com.join.back.repository.GroupChatRepository;
import com.join.back.repository.GroupGatheringRepository;
import com.join.back.repository.GroupMemberRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupGatheringRepository groupGatheringRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupChatRepository groupChatRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessengerNotificationService messengerNotificationService;
    private final UserBlockService userBlockService;

    @Transactional(transactionManager = "transactionManager")
    public GroupResponse createGroup(Long userId, Long eventId, CreateGroupRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + eventId));

        if (event.getEventDate().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Cannot create a group for a past event");
        }

        boolean alreadyInGroup = groupGatheringRepository
                .existsActiveGroupMemberByEventIdAndUserId(eventId, userId);
        if (alreadyInGroup) {
            throw new UserActionException("Ты уже в компании на это событие");
        }

        LocalDateTime now = LocalDateTime.now();
        GroupGathering group = GroupGathering.builder()
                .eventId(eventId)
                .creatorId(userId)
                .title(request.title())
                .description(request.description())
                .maxSize(request.maxSize())
                .status(GroupStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();
        group = groupGatheringRepository.save(group);

        GroupMember creatorMember = GroupMember.builder()
                .groupId(group.getId())
                .userId(userId)
                .role(GroupMemberRole.CREATOR)
                .status(GroupMemberStatus.ACTIVE)
                .joinedAt(now)
                .build();
        groupMemberRepository.save(creatorMember);

        GroupChat groupChat = GroupChat.builder()
                .groupId(group.getId())
                .eventId(eventId)
                .createdAt(now)
                .build();
        groupChat = groupChatRepository.save(groupChat);

        return buildGroupResponse(group, event, groupChat, List.of(creatorMember));
    }

    @Transactional(transactionManager = "transactionManager")
    public JoinGroupResponse joinGroup(Long userId, Long groupId) {
        GroupGathering group = groupGatheringRepository.findByIdWithLock(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + groupId));

        if (group.getStatus() != GroupStatus.OPEN) {
            throw new UserActionException("Набор в эту компанию закрыт");
        }

        if (group.getCreatorId().equals(userId)) {
            throw new UserActionException("Ты уже состоишь в этой компании");
        }

        boolean alreadyMember = groupMemberRepository
                .existsByGroupIdAndUserIdAndStatus(groupId, userId, GroupMemberStatus.ACTIVE);
        if (alreadyMember) {
            throw new UserActionException("Ты уже состоишь в этой компании");
        }

        boolean alreadyInEventGroup = groupGatheringRepository
                .existsActiveGroupMemberByEventIdAndUserId(group.getEventId(), userId);
        if (alreadyInEventGroup) {
            throw new UserActionException("Ты уже в компании на это событие");
        }

        java.util.Set<Long> blocked = userBlockService.relatedUserIds(userId);
        if (!blocked.isEmpty() && groupMemberRepository.findByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE).stream()
                .anyMatch(m -> blocked.contains(m.getUserId()))) {
            throw new UserActionException("Не получится вступить: в этой компании человек из чёрного списка");
        }

        User joiner = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        List<Long> memberIds = groupMemberRepository.findByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE).stream()
                .map(GroupMember::getUserId)
                .toList();
        if (userRepository.findAllById(memberIds).stream().anyMatch(m -> !AgePolicy.canMeet(joiner, m))) {
            throw new UserActionException("Эта компания для другого возраста: до 18 лет JOIN собирает компании только из сверстников");
        }

        LocalDateTime now = LocalDateTime.now();
        // Reactivate existing LEFT record if user is rejoining, otherwise create new
        Optional<GroupMember> existingMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId);
        GroupMember member;
        if (existingMember.isPresent() && existingMember.get().getStatus() == GroupMemberStatus.LEFT) {
            member = existingMember.get();
            member.setStatus(GroupMemberStatus.ACTIVE);
            member.setRole(GroupMemberRole.MEMBER);
            member.setJoinedAt(now);
            member.setLeftAt(null);
        } else {
            member = GroupMember.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .role(GroupMemberRole.MEMBER)
                    .status(GroupMemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();
        }
        groupMemberRepository.save(member);

        long activeCount = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
        if (activeCount >= group.getMaxSize()) {
            group.setStatus(GroupStatus.FULL);
            group.setUpdatedAt(now);
            groupGatheringRepository.save(group);
        }

        GroupChat groupChat = groupChatRepository.findByGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group chat not found for group: " + groupId));

        return new JoinGroupResponse(groupId, groupChat.getId(), "You have successfully joined the group");
    }

    @Transactional(transactionManager = "transactionManager")
    public LeaveGroupResponse leaveGroup(Long userId, Long groupId) {
        GroupGathering group = groupGatheringRepository.findByIdWithLock(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + groupId));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalStateException("User is not a member of this group"));

        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new IllegalStateException("User is not an active member of this group");
        }

        LocalDateTime now = LocalDateTime.now();
        member.setStatus(GroupMemberStatus.LEFT);
        member.setLeftAt(now);
        groupMemberRepository.save(member);

        if (member.getRole() == GroupMemberRole.CREATOR) {
            List<GroupMember> remaining = groupMemberRepository
                    .findActiveByGroupIdOrderByJoinedAt(groupId)
                    .stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .collect(Collectors.toList());

            if (remaining.isEmpty()) {
                group.setStatus(GroupStatus.CLOSED);
            } else {
                GroupMember newCreator = remaining.get(0);
                newCreator.setRole(GroupMemberRole.CREATOR);
                groupMemberRepository.save(newCreator);
                group.setCreatorId(newCreator.getUserId());

                long activeCount = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
                if (group.getStatus() == GroupStatus.FULL && activeCount < group.getMaxSize()) {
                    group.setStatus(GroupStatus.OPEN);
                }
            }
        } else {
            long activeCount = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
            if (group.getStatus() == GroupStatus.FULL && activeCount < group.getMaxSize()) {
                group.setStatus(GroupStatus.OPEN);
            }
        }

        group.setUpdatedAt(now);
        groupGatheringRepository.save(group);

        return new LeaveGroupResponse(groupId, "You have left the group");
    }

    /** Open companies the viewer may join: teenagers and adults don't see each other's (AgePolicy). */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<GroupResponse> getGroupsForEvent(Long eventId, Long viewerId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + eventId));
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + viewerId));

        Page<GroupGathering> groups = groupGatheringRepository
                .findByEventIdAndStatus(eventId, GroupStatus.OPEN, pageable);
        Map<Long, User> creators = userRepository.findAllById(
                        groups.getContent().stream().map(GroupGathering::getCreatorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<GroupResponse> responses = groups.getContent().stream()
                .filter(group -> {
                    User creator = creators.get(group.getCreatorId());
                    return creator == null || AgePolicy.canMeet(viewer, creator);
                })
                .map(group -> {
                    GroupChat chat = groupChatRepository.findByGroupId(group.getId()).orElse(null);
                    List<GroupMember> members = groupMemberRepository
                            .findActiveByGroupIdOrderByJoinedAt(group.getId());
                    return buildGroupResponse(group, event, chat, members);
                })
                .collect(Collectors.toList());

        long hidden = groups.getNumberOfElements() - responses.size();
        return new PageImpl<>(responses, pageable, groups.getTotalElements() - hidden);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<GroupResponse> getMyGroups(Long userId, Pageable pageable) {
        Page<GroupMember> memberships = groupMemberRepository.findActiveByUserId(userId, pageable);

        List<Long> groupIds = memberships.getContent().stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        if (groupIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<GroupGathering> groups = groupGatheringRepository.findAllById(groupIds);
        Map<Long, GroupGathering> groupMap = groups.stream()
                .collect(Collectors.toMap(GroupGathering::getId, g -> g));

        List<Long> eventIds = groups.stream().map(GroupGathering::getEventId).distinct().collect(Collectors.toList());
        Map<Long, Event> eventMap = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        List<GroupResponse> responses = groupIds.stream()
                .filter(groupMap::containsKey)
                .map(groupId -> {
                    GroupGathering group = groupMap.get(groupId);
                    Event event = eventMap.get(group.getEventId());
                    GroupChat chat = groupChatRepository.findByGroupId(groupId).orElse(null);
                    List<GroupMember> members = groupMemberRepository
                            .findActiveByGroupIdOrderByJoinedAt(groupId);
                    return buildGroupResponse(group, event, chat, members);
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, memberships.getTotalElements());
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public GroupResponse getGroupById(Long groupId, Long viewerId) {
        GroupGathering group = groupGatheringRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + groupId));
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + viewerId));
        User creator = userRepository.findById(group.getCreatorId()).orElse(null);
        if (creator != null && !AgePolicy.canMeet(viewer, creator)) {
            throw new EntityNotFoundException("Group not found with id: " + groupId);
        }

        Event event = eventRepository.findById(group.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + group.getEventId()));

        GroupChat chat = groupChatRepository.findByGroupId(groupId).orElse(null);
        List<GroupMember> members = groupMemberRepository.findActiveByGroupIdOrderByJoinedAt(groupId);

        return buildGroupResponse(group, event, chat, members);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public GroupStatsResponse getGroupStats(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EntityNotFoundException("Event not found with id: " + eventId);
        }

        long openGroupsCount = groupGatheringRepository
                .findByEventIdAndStatus(eventId, GroupStatus.OPEN, Pageable.unpaged())
                .getTotalElements();

        long totalActiveMembers = groupGatheringRepository
                .findByEventIdAndStatus(eventId, GroupStatus.OPEN, Pageable.unpaged())
                .getContent().stream()
                .mapToLong(g -> groupMemberRepository.countByGroupIdAndStatus(g.getId(), GroupMemberStatus.ACTIVE))
                .sum();

        return new GroupStatsResponse(eventId, openGroupsCount, totalActiveMembers);
    }

    @Transactional(transactionManager = "transactionManager")
    public GroupChatMessageResponse sendGroupMessage(Long groupChatId, Long senderId, String text) {
        GroupChat groupChat = groupChatRepository.findById(groupChatId)
                .orElseThrow(() -> new EntityNotFoundException("Group chat not found with id: " + groupChatId));

        boolean isActiveMember = groupMemberRepository
                .existsByGroupIdAndUserIdAndStatus(groupChat.getGroupId(), senderId, GroupMemberStatus.ACTIVE);
        if (!isActiveMember) {
            throw new AccessDeniedException("User is not an active member of this group");
        }

        LocalDateTime now = LocalDateTime.now();
        GroupChatMessage message = GroupChatMessage.builder()
                .groupChatId(groupChatId)
                .senderId(senderId)
                .text(text)
                .createdAt(now)
                .isRead(false)
                .build();
        message = groupChatMessageRepository.save(message);

        User sender = userRepository.findById(senderId).orElse(null);

        GroupChatMessageResponse response = new GroupChatMessageResponse(
                message.getId(),
                senderId,
                sender != null ? sender.getFirstName() : null,
                sender != null ? sender.getPhoto() : null,
                message.getText(),
                message.getCreatedAt()
        );

        messagingTemplate.convertAndSend("/topic/group/" + groupChatId, response);

        // Send MAX notifications to all group members except sender
        try {
            Event event = eventRepository.findById(groupChat.getEventId()).orElse(null);
            String eventTitle = event != null ? event.getTitle() : null;
            String senderName = sender != null ? sender.getFirstName() : null;

            List<GroupMember> members = groupMemberRepository
                    .findByGroupIdAndStatus(groupChat.getGroupId(), GroupMemberStatus.ACTIVE);

            for (GroupMember member : members) {
                if (member.getUserId().equals(senderId)) continue;
                User memberUser = userRepository.findById(member.getUserId()).orElse(null);
                messengerNotificationService.sendGroupMessageNotification(
                        memberUser,
                        senderName,
                        eventTitle,
                        text,
                        groupChatId
                );
            }
        } catch (Exception e) {
            // Don't fail the message send if notifications fail
        }

        return response;
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<GroupChatMessageResponse> getGroupMessages(Long groupChatId, Long userId, Pageable pageable) {
        GroupChat groupChat = groupChatRepository.findById(groupChatId)
                .orElseThrow(() -> new EntityNotFoundException("Group chat not found with id: " + groupChatId));

        boolean isActiveMember = groupMemberRepository
                .existsByGroupIdAndUserIdAndStatus(groupChat.getGroupId(), userId, GroupMemberStatus.ACTIVE);
        if (!isActiveMember) {
            throw new AccessDeniedException("User is not an active member of this group");
        }

        List<Long> senderIds = groupMemberRepository
                .findByGroupIdAndStatus(groupChat.getGroupId(), GroupMemberStatus.ACTIVE)
                .stream().map(GroupMember::getUserId).collect(Collectors.toList());

        Map<Long, User> usersMap = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return groupChatMessageRepository
                .findByGroupChatIdOrderByCreatedAtAsc(groupChatId, pageable)
                .map(msg -> {
                    User s = usersMap.get(msg.getSenderId());
                    return new GroupChatMessageResponse(
                            msg.getId(),
                            msg.getSenderId(),
                            s != null ? s.getFirstName() : null,
                            s != null ? s.getPhoto() : null,
                            msg.getText(),
                            msg.getCreatedAt()
                    );
                });
    }

    private GroupResponse buildGroupResponse(GroupGathering group, Event event,
                                              GroupChat groupChat, List<GroupMember> members) {
        List<Long> userIds = members.stream().map(GroupMember::getUserId).collect(Collectors.toList());
        Map<Long, User> usersMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<GroupMemberResponse> memberResponses = members.stream()
                .map(m -> {
                    User u = usersMap.get(m.getUserId());
                    return new GroupMemberResponse(
                            m.getUserId(),
                            u != null ? u.getFirstName() : null,
                            u != null ? u.getPhoto() : null,
                            m.getRole().name(),
                            m.getJoinedAt()
                    );
                })
                .collect(Collectors.toList());

        GroupMemberResponse creatorResponse = memberResponses.stream()
                .filter(m -> m.role().equals(GroupMemberRole.CREATOR.name()))
                .findFirst()
                .orElse(null);

        return new GroupResponse(
                group.getId(),
                group.getEventId(),
                event != null ? event.getTitle() : null,
                event != null ? event.getEventDate() : null,
                group.getTitle(),
                group.getDescription(),
                group.getMaxSize(),
                members.size(),
                group.getStatus().name(),
                groupChat != null ? groupChat.getId() : null,
                creatorResponse,
                memberResponses,
                group.getCreatedAt()
        );
    }
}
