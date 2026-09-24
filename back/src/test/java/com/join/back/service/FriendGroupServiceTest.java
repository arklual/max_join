package com.join.back.service;

import com.join.back.model.dto.CreateFriendGroupRequest;
import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.FriendGroupResponse;
import com.join.back.model.dto.JoinFriendGroupResponse;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendGroupServiceTest {

    private static final Long CREATOR_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_USER_ID = 3L;
    private static final Long GROUP_ID = 100L;
    private static final String INVITE_CODE = "abc12345";
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final LocalDate PAST_DATE = LocalDate.now().minusDays(7);

    @Mock
    private FriendGroupRepository friendGroupRepository;
    @Mock
    private FriendGroupMemberRepository friendGroupMemberRepository;
    @Mock
    private EventLikeRepository eventLikeRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendGroupService service;

    private FriendGroup group;
    private FriendGroupMember creatorMember;
    private FriendGroupMember secondMember;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        group = FriendGroup.builder()
                .id(GROUP_ID)
                .name("Squad")
                .creatorId(CREATOR_ID)
                .inviteCode(INVITE_CODE)
                .maxSize(5)
                .createdAt(now)
                .updatedAt(now)
                .archived(false)
                .build();
        creatorMember = FriendGroupMember.builder()
                .id(1L)
                .friendGroupId(GROUP_ID)
                .userId(CREATOR_ID)
                .role(FriendGroupMemberRole.CREATOR)
                .status(FriendGroupMemberStatus.ACTIVE)
                .joinedAt(now)
                .build();
        secondMember = FriendGroupMember.builder()
                .id(2L)
                .friendGroupId(GROUP_ID)
                .userId(MEMBER_ID)
                .role(FriendGroupMemberRole.MEMBER)
                .status(FriendGroupMemberStatus.ACTIVE)
                .joinedAt(now.plusMinutes(5))
                .build();
    }

    @Test
    void create_happyPath_savesGroupAndCreatorMember() {
        when(friendGroupRepository.saveAndFlush(any(FriendGroup.class))).thenAnswer(inv -> {
            FriendGroup saved = inv.getArgument(0);
            saved.setId(GROUP_ID);
            return saved;
        });
        when(friendGroupMemberRepository.save(any(FriendGroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember));
        when(userRepository.findAllById(any())).thenReturn(List.of(buildUser(CREATOR_ID, "Alex")));

        FriendGroupResponse result = service.create(CREATOR_ID, new CreateFriendGroupRequest("Squad", 5));

        assertNotNull(result);
        assertEquals("Squad", result.name());
        assertEquals(5, result.maxSize());
        assertEquals(1, result.currentSize());
        assertEquals(CREATOR_ID, result.creator().userId());
        verify(friendGroupRepository).saveAndFlush(any(FriendGroup.class));
        verify(friendGroupMemberRepository).save(any(FriendGroupMember.class));
    }

    @Test
    void create_inviteCodeCollision_retriesAndSucceeds() {
        when(friendGroupRepository.saveAndFlush(any(FriendGroup.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(inv -> {
                    FriendGroup saved = inv.getArgument(0);
                    saved.setId(GROUP_ID);
                    return saved;
                });
        when(friendGroupMemberRepository.save(any(FriendGroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember));
        when(userRepository.findAllById(any())).thenReturn(List.of(buildUser(CREATOR_ID, "Alex")));

        FriendGroupResponse result = service.create(CREATOR_ID, new CreateFriendGroupRequest("Squad", 5));

        assertNotNull(result);
        verify(friendGroupRepository, atLeast(2)).saveAndFlush(any(FriendGroup.class));
    }

    @Test
    void create_inviteCodeCollisionExhausted_throws() {
        when(friendGroupRepository.saveAndFlush(any(FriendGroup.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(IllegalStateException.class,
                () -> service.create(CREATOR_ID, new CreateFriendGroupRequest("Squad", 5)));
    }

    @Test
    void joinByInviteCode_happyPath_addsActiveMember() {
        when(friendGroupRepository.findByInviteCodeWithLock(INVITE_CODE)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.countByFriendGroupIdAndStatus(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(1L);
        when(friendGroupMemberRepository.findByFriendGroupIdAndUserId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        JoinFriendGroupResponse response = service.joinByInviteCode(MEMBER_ID, INVITE_CODE);

        assertEquals(GROUP_ID, response.friendGroupId());
        verify(friendGroupMemberRepository).save(any(FriendGroupMember.class));
    }

    @Test
    void joinByInviteCode_groupFull_throws() {
        group.setMaxSize(2);
        when(friendGroupRepository.findByInviteCodeWithLock(INVITE_CODE)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.countByFriendGroupIdAndStatus(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(2L);

        assertThrows(IllegalStateException.class,
                () -> service.joinByInviteCode(THIRD_USER_ID, INVITE_CODE));
        verify(friendGroupMemberRepository, never()).save(any());
    }

    @Test
    void joinByInviteCode_alreadyActiveMember_throws() {
        when(friendGroupRepository.findByInviteCodeWithLock(INVITE_CODE)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.countByFriendGroupIdAndStatus(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(1L);
        when(friendGroupMemberRepository.findByFriendGroupIdAndUserId(GROUP_ID, CREATOR_ID))
                .thenReturn(Optional.of(creatorMember));

        assertThrows(IllegalStateException.class,
                () -> service.joinByInviteCode(CREATOR_ID, INVITE_CODE));
    }

    @Test
    void joinByInviteCode_invalidOrArchivedCode_throws() {
        when(friendGroupRepository.findByInviteCodeWithLock("nope")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.joinByInviteCode(MEMBER_ID, "nope"));
    }

    @Test
    void leave_creatorWithRemainingMembers_handovers_role() {
        when(friendGroupRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.findByFriendGroupIdAndUserId(GROUP_ID, CREATOR_ID))
                .thenReturn(Optional.of(creatorMember));
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember, secondMember));

        service.leave(CREATOR_ID, GROUP_ID);

        assertEquals(FriendGroupMemberStatus.LEFT, creatorMember.getStatus());
        assertEquals(FriendGroupMemberRole.CREATOR, secondMember.getRole());
        assertEquals(MEMBER_ID, group.getCreatorId());
        assertEquals(Boolean.FALSE, group.getArchived(), "group should not be archived when members remain");
    }

    @Test
    void leave_creatorAloneNoRemaining_archivesGroup() {
        when(friendGroupRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.findByFriendGroupIdAndUserId(GROUP_ID, CREATOR_ID))
                .thenReturn(Optional.of(creatorMember));
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember));

        service.leave(CREATOR_ID, GROUP_ID);

        assertEquals(FriendGroupMemberStatus.LEFT, creatorMember.getStatus());
        assertEquals(Boolean.TRUE, group.getArchived(), "lonely creator leaving should archive the group");
    }

    @Test
    void getById_archivedGroup_throwsNotFound() {
        group.setArchived(true);
        when(friendGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThrows(EntityNotFoundException.class,
                () -> service.getById(GROUP_ID, CREATOR_ID));
    }

    @Test
    void getMyGroups_skipsArchivedAndMissingGroups() {
        FriendGroup archived = FriendGroup.builder()
                .id(GROUP_ID + 1)
                .name("Old")
                .creatorId(CREATOR_ID)
                .inviteCode("zzz")
                .maxSize(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .archived(true)
                .build();
        FriendGroupMember activeInArchived = FriendGroupMember.builder()
                .id(3L)
                .friendGroupId(archived.getId())
                .userId(CREATOR_ID)
                .role(FriendGroupMemberRole.CREATOR)
                .status(FriendGroupMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<FriendGroupMember> memberships = new PageImpl<>(List.of(creatorMember, activeInArchived), pageable, 2);
        when(friendGroupMemberRepository.findByUserIdAndStatus(CREATOR_ID, FriendGroupMemberStatus.ACTIVE, pageable))
                .thenReturn(memberships);
        when(friendGroupRepository.findAllById(List.of(GROUP_ID, archived.getId())))
                .thenReturn(List.of(group, archived));
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember));
        when(userRepository.findAllById(any())).thenReturn(List.of(buildUser(CREATOR_ID, "Alex")));

        Page<FriendGroupResponse> result = service.getMyGroups(CREATOR_ID, pageable);

        assertEquals(1, result.getContent().size(), "archived group should be filtered out");
        assertEquals(GROUP_ID, result.getContent().get(0).id());
    }

    @Test
    void getCommonEvents_intersection_returnsFutureEventsLikedByAllMembers() {
        Event futureEvent = buildEvent(50L, FUTURE_DATE);
        Event pastEvent = buildEvent(51L, PAST_DATE);

        when(friendGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.existsByFriendGroupIdAndUserIdAndStatus(GROUP_ID, CREATOR_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(true);
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember, secondMember));
        when(eventLikeRepository.findCommonEventIdsByUserIds(anySet(), eq(2L)))
                .thenReturn(List.of(50L, 51L));
        when(eventRepository.findAllById(any())).thenReturn(List.of(futureEvent, pastEvent));
        when(eventMapper.toCardResponse(futureEvent)).thenReturn(buildCard(50L, FUTURE_DATE));

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = service.getCommonEvents(GROUP_ID, CREATOR_ID, pageable);

        assertEquals(1, result.getTotalElements(), "past events must be filtered out");
        assertEquals(50L, result.getContent().get(0).id());
        assertTrue(result.getContent().get(0).liked());
    }

    @Test
    void getCommonEvents_oneActiveMember_returnsEmpty() {
        when(friendGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.existsByFriendGroupIdAndUserIdAndStatus(GROUP_ID, CREATOR_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(true);
        when(friendGroupMemberRepository.findByFriendGroupIdAndStatusOrderByJoinedAtAsc(GROUP_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(creatorMember));

        Page<EventCardResponse> result = service.getCommonEvents(GROUP_ID, CREATOR_ID, PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
        verify(eventLikeRepository, never()).findCommonEventIdsByUserIds(any(), anyLong());
    }

    @Test
    void getCommonEvents_notMember_throwsAccessDenied() {
        when(friendGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(friendGroupMemberRepository.existsByFriendGroupIdAndUserIdAndStatus(GROUP_ID, THIRD_USER_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.getCommonEvents(GROUP_ID, THIRD_USER_ID, PageRequest.of(0, 10)));
    }

    @Test
    void getCommonEvents_archivedGroup_throwsNotFound() {
        group.setArchived(true);
        when(friendGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThrows(EntityNotFoundException.class,
                () -> service.getCommonEvents(GROUP_ID, CREATOR_ID, PageRequest.of(0, 10)));
    }

    private User buildUser(Long id, String firstName) {
        User u = new User();
        u.setId(id);
        u.setFirstName(firstName);
        return u;
    }

    private Event buildEvent(Long id, LocalDate date) {
        Event e = new Event();
        e.setId(id);
        e.setTitle("E" + id);
        e.setEventDate(date);
        e.setType(EventType.MUSIC);
        return e;
    }

    private EventCardResponse buildCard(Long id, LocalDate date) {
        return new EventCardResponse(id, "E" + id, EventType.MUSIC, null,
                BigDecimal.ZERO, null, null, null, date, null, null, false, false, false, 0L);
    }
}
