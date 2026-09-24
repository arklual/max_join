package com.join.back.service;

import com.join.back.model.dto.CreateFriendGroupRequest;
import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.FriendGroupMemberResponse;
import com.join.back.model.dto.FriendGroupResponse;
import com.join.back.model.dto.JoinFriendGroupResponse;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.FriendGroup;
import com.join.back.model.entity.FriendGroupMember;
import com.join.back.model.entity.FriendGroupMemberRole;
import com.join.back.model.entity.FriendGroupMemberStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.FriendGroupMemberRepository;
import com.join.back.repository.FriendGroupRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendGroupService {

    private static final String INVITE_CODE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FriendGroupRepository friendGroupRepository;
    private final FriendGroupMemberRepository friendGroupMemberRepository;
    private final EventLikeRepository eventLikeRepository;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserRepository userRepository;

    @Transactional(transactionManager = "transactionManager")
    public FriendGroupResponse create(Long creatorUserId, CreateFriendGroupRequest request) {
        LocalDateTime now = LocalDateTime.now();
        FriendGroup saved = saveGroupWithUniqueInviteCode(creatorUserId, request, now);

        FriendGroupMember creator = FriendGroupMember.builder()
                .friendGroupId(saved.getId())
                .userId(creatorUserId)
                .role(FriendGroupMemberRole.CREATOR)
                .status(FriendGroupMemberStatus.ACTIVE)
                .joinedAt(now)
                .build();
        friendGroupMemberRepository.save(creator);

        return buildResponse(saved);
    }

    /**
     * Persists the friend group with a freshly generated invite code, retrying on
     * unique-constraint collisions. The DB unique constraint is the authoritative
     * guarantee — the retry loop just maps it to a clean error rather than a 500.
     */
    private FriendGroup saveGroupWithUniqueInviteCode(Long creatorUserId,
                                                      CreateFriendGroupRequest request,
                                                      LocalDateTime now) {
        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            FriendGroup group = FriendGroup.builder()
                    .name(request.name())
                    .creatorId(creatorUserId)
                    .inviteCode(generateInviteCode())
                    .maxSize(request.maxSize() != null ? request.maxSize() : 10)
                    .createdAt(now)
                    .updatedAt(now)
                    .archived(false)
                    .build();
            try {
                return friendGroupRepository.saveAndFlush(group);
            } catch (DataIntegrityViolationException e) {
                lastFailure = e;
            }
        }
        throw new IllegalStateException(
                "Failed to generate unique invite code after " + INVITE_CODE_MAX_ATTEMPTS + " attempts",
                lastFailure);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<FriendGroupResponse> getMyGroups(Long userId, Pageable pageable) {
        Page<FriendGroupMember> memberships = friendGroupMemberRepository
                .findByUserIdAndStatus(userId, FriendGroupMemberStatus.ACTIVE, pageable);

        if (memberships.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> groupIds = memberships.getContent().stream()
                .map(FriendGroupMember::getFriendGroupId)
                .collect(Collectors.toList());

        Map<Long, FriendGroup> groupMap = friendGroupRepository.findAllById(groupIds).stream()
                .filter(g -> !Boolean.TRUE.equals(g.getArchived()))
                .collect(Collectors.toMap(FriendGroup::getId, g -> g));

        List<FriendGroupResponse> responses = memberships.getContent().stream()
                .map(m -> groupMap.get(m.getFriendGroupId()))
                .filter(Objects::nonNull)
                .map(this::buildResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, memberships.getTotalElements());
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public FriendGroupResponse getById(Long groupId, Long currentUserId) {
        FriendGroup group = friendGroupRepository.findById(groupId)
                .filter(g -> !Boolean.TRUE.equals(g.getArchived()))
                .orElseThrow(() -> new EntityNotFoundException("Friend group not found with id: " + groupId));
        assertActiveMember(groupId, currentUserId);
        return buildResponse(group);
    }

    @Transactional(transactionManager = "transactionManager")
    public JoinFriendGroupResponse joinByInviteCode(Long userId, String inviteCode) {
        FriendGroup locked = friendGroupRepository.findByInviteCodeWithLock(inviteCode)
                .orElseThrow(() -> new EntityNotFoundException("Invalid invite code"));

        long activeCount = friendGroupMemberRepository
                .countByFriendGroupIdAndStatus(locked.getId(), FriendGroupMemberStatus.ACTIVE);
        if (activeCount >= locked.getMaxSize()) {
            throw new IllegalStateException("Friend group is full");
        }

        Optional<FriendGroupMember> existing = friendGroupMemberRepository
                .findByFriendGroupIdAndUserId(locked.getId(), userId);

        LocalDateTime now = LocalDateTime.now();

        if (existing.isPresent()) {
            FriendGroupMember member = existing.get();
            if (member.getStatus() == FriendGroupMemberStatus.ACTIVE) {
                throw new IllegalStateException("User is already a member of this group");
            }
            member.setStatus(FriendGroupMemberStatus.ACTIVE);
            member.setJoinedAt(now);
            member.setLeftAt(null);
            friendGroupMemberRepository.save(member);
        } else {
            FriendGroupMember member = FriendGroupMember.builder()
                    .friendGroupId(locked.getId())
                    .userId(userId)
                    .role(FriendGroupMemberRole.MEMBER)
                    .status(FriendGroupMemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();
            friendGroupMemberRepository.save(member);
        }

        return new JoinFriendGroupResponse(locked.getId(), locked.getName(),
                "You have successfully joined the group");
    }

    @Transactional(transactionManager = "transactionManager")
    public void leave(Long userId, Long groupId) {
        FriendGroup group = friendGroupRepository.findByIdWithLock(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Friend group not found with id: " + groupId));

        FriendGroupMember member = friendGroupMemberRepository
                .findByFriendGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalStateException("User is not a member of this group"));

        if (member.getStatus() != FriendGroupMemberStatus.ACTIVE) {
            throw new IllegalStateException("User is not an active member");
        }

        boolean wasCreator = (member.getRole() == FriendGroupMemberRole.CREATOR);

        LocalDateTime now = LocalDateTime.now();
        member.setStatus(FriendGroupMemberStatus.LEFT);
        member.setLeftAt(now);
        friendGroupMemberRepository.save(member);

        if (wasCreator) {
            List<FriendGroupMember> remaining = friendGroupMemberRepository
                    .findByFriendGroupIdAndStatusOrderByJoinedAtAsc(groupId, FriendGroupMemberStatus.ACTIVE)
                    .stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .collect(Collectors.toList());

            if (!remaining.isEmpty()) {
                FriendGroupMember newCreator = remaining.get(0);
                newCreator.setRole(FriendGroupMemberRole.CREATOR);
                friendGroupMemberRepository.save(newCreator);
                group.setCreatorId(newCreator.getUserId());
                group.setUpdatedAt(now);
                friendGroupRepository.save(group);
            } else {
                // No one left — archive the group instead of leaving it orphan.
                // Soft-archive keeps history (members and messages) for analytics
                // and a potential future "restore" endpoint.
                group.setArchived(true);
                group.setUpdatedAt(now);
                friendGroupRepository.save(group);
            }
        }
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<EventCardResponse> getCommonEvents(Long groupId, Long currentUserId, Pageable pageable) {
        FriendGroup group = friendGroupRepository.findById(groupId)
                .filter(g -> !Boolean.TRUE.equals(g.getArchived()))
                .orElseThrow(() -> new EntityNotFoundException("Friend group not found with id: " + groupId));
        assertActiveMember(group.getId(), currentUserId);

        List<FriendGroupMember> activeMembers = friendGroupMemberRepository
                .findByFriendGroupIdAndStatusOrderByJoinedAtAsc(groupId, FriendGroupMemberStatus.ACTIVE);

        if (activeMembers.size() < 2) {
            return Page.empty(pageable);
        }

        Set<Long> userIds = activeMembers.stream()
                .map(FriendGroupMember::getUserId)
                .collect(Collectors.toSet());

        List<Long> commonEventIds = eventLikeRepository
                .findCommonEventIdsByUserIds(userIds, userIds.size());
        if (commonEventIds.isEmpty()) {
            return Page.empty(pageable);
        }

        LocalDate today = LocalDate.now();
        List<Event> events = eventRepository.findAllById(commonEventIds).stream()
                .filter(e -> e.getEventDate() != null && !e.getEventDate().isBefore(today))
                .sorted(Comparator.comparing(Event::getEventDate))
                .collect(Collectors.toList());

        int total = events.size();
        int start = (int) pageable.getOffset();
        if (start >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        int end = Math.min(start + pageable.getPageSize(), total);

        List<EventCardResponse> pageContent = events.subList(start, end).stream()
                .map(eventMapper::toCardResponse)
                .map(card -> card.withLiked(true))
                .collect(Collectors.toList());

        return new PageImpl<>(pageContent, pageable, total);
    }

    void assertActiveMember(Long groupId, Long userId) {
        boolean isMember = friendGroupMemberRepository
                .existsByFriendGroupIdAndUserIdAndStatus(groupId, userId, FriendGroupMemberStatus.ACTIVE);
        if (!isMember) {
            throw new AccessDeniedException("User is not an active member of this friend group");
        }
    }

    private FriendGroupResponse buildResponse(FriendGroup group) {
        List<FriendGroupMember> activeMembers = friendGroupMemberRepository
                .findByFriendGroupIdAndStatusOrderByJoinedAtAsc(group.getId(), FriendGroupMemberStatus.ACTIVE);

        List<Long> userIds = activeMembers.stream()
                .map(FriendGroupMember::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<FriendGroupMemberResponse> memberResponses = new ArrayList<>(activeMembers.size());
        for (FriendGroupMember m : activeMembers) {
            User u = users.get(m.getUserId());
            memberResponses.add(new FriendGroupMemberResponse(
                    m.getUserId(),
                    u != null ? u.getFirstName() : null,
                    u != null ? u.getPhoto() : null,
                    m.getRole().name(),
                    m.getJoinedAt()
            ));
        }

        FriendGroupMemberResponse creator = memberResponses.stream()
                .filter(m -> FriendGroupMemberRole.CREATOR.name().equals(m.role()))
                .findFirst()
                .orElse(null);

        return new FriendGroupResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.getMaxSize(),
                activeMembers.size(),
                creator,
                memberResponses,
                group.getCreatedAt()
        );
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_ALPHABET.charAt(RANDOM.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
