package com.join.back.service;

import com.join.back.model.dto.CreateGroupRequest;
import com.join.back.model.dto.GroupResponse;
import com.join.back.model.dto.JoinGroupResponse;
import com.join.back.model.dto.LeaveGroupResponse;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.GroupChat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final Long GROUP_ID = 100L;
    private static final Long CHAT_ID = 200L;
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(10);
    private static final LocalDate PAST_DATE = LocalDate.now().minusDays(1);

    @Mock
    private GroupGatheringRepository groupGatheringRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupChatRepository groupChatRepository;
    @Mock
    private GroupChatMessageRepository groupChatMessageRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GroupService groupService;

    private Event futureEvent;
    private Event pastEvent;
    private GroupGathering openGroup;
    private GroupMember creatorMember;
    private GroupChat groupChat;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).maxId(12345L).firstName("Алексей").build();

        futureEvent = Event.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .type(EventType.MUSIC)
                .eventDate(FUTURE_DATE)
                .price(BigDecimal.ZERO)
                .build();

        pastEvent = Event.builder()
                .id(EVENT_ID)
                .title("Past Event")
                .type(EventType.MUSIC)
                .eventDate(PAST_DATE)
                .price(BigDecimal.ZERO)
                .build();

        openGroup = GroupGathering.builder()
                .id(GROUP_ID)
                .eventId(EVENT_ID)
                .creatorId(USER_ID)
                .title("Test Group")
                .maxSize(5)
                .status(GroupStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();

        creatorMember = GroupMember.builder()
                .id(1L)
                .groupId(GROUP_ID)
                .userId(USER_ID)
                .role(GroupMemberRole.CREATOR)
                .status(GroupMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        groupChat = GroupChat.builder()
                .id(CHAT_ID)
                .groupId(GROUP_ID)
                .eventId(EVENT_ID)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ========== createGroup tests ==========

    @Test
    void createGroupShouldSucceed() {
        CreateGroupRequest request = new CreateGroupRequest("Test Group", "Description", 5);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(groupGatheringRepository.save(any())).thenReturn(openGroup);
        when(groupMemberRepository.save(any())).thenReturn(creatorMember);
        when(groupChatRepository.save(any())).thenReturn(groupChat);
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        GroupResponse response = groupService.createGroup(USER_ID, EVENT_ID, request);

        assertNotNull(response);
        assertEquals(GROUP_ID, response.id());
        assertEquals(EVENT_ID, response.eventId());
        assertEquals(CHAT_ID, response.groupChatId());
        verify(groupGatheringRepository).save(any());
        verify(groupMemberRepository).save(any());
        verify(groupChatRepository).save(any());
    }

    @Test
    void createGroupShouldThrowForPastEvent() {
        CreateGroupRequest request = new CreateGroupRequest("Test Group", null, 5);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent));

        assertThrows(IllegalArgumentException.class,
                () -> groupService.createGroup(USER_ID, EVENT_ID, request));
    }

    @Test
    void createGroupShouldThrowWhenAlreadyInGroup() {
        CreateGroupRequest request = new CreateGroupRequest("Test Group", null, 5);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> groupService.createGroup(USER_ID, EVENT_ID, request));
    }

    @Test
    void createGroupShouldThrowForNonExistentEvent() {
        CreateGroupRequest request = new CreateGroupRequest("Test Group", null, 5);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> groupService.createGroup(USER_ID, EVENT_ID, request));
    }

    // ========== joinGroup tests ==========

    @Test
    void joinGroupShouldSucceed() {
        Long newUserId = 2L;
        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, newUserId, GroupMemberStatus.ACTIVE))
                .thenReturn(false);
        when(groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(EVENT_ID, newUserId))
                .thenReturn(false);
        when(groupMemberRepository.save(any())).thenReturn(GroupMember.builder().build());
        when(groupMemberRepository.countByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(2L);
        when(groupChatRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(groupChat));

        JoinGroupResponse response = groupService.joinGroup(newUserId, GROUP_ID);

        assertNotNull(response);
        assertEquals(GROUP_ID, response.groupId());
        assertEquals(CHAT_ID, response.groupChatId());
        verify(groupMemberRepository).save(any());
    }

    @Test
    void joinGroupShouldSetStatusFullWhenMaxReached() {
        Long newUserId = 2L;
        GroupGathering group = GroupGathering.builder()
                .id(GROUP_ID)
                .eventId(EVENT_ID)
                .creatorId(USER_ID)
                .maxSize(2)
                .status(GroupStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();

        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, newUserId, GroupMemberStatus.ACTIVE))
                .thenReturn(false);
        when(groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(EVENT_ID, newUserId))
                .thenReturn(false);
        when(groupMemberRepository.save(any())).thenReturn(GroupMember.builder().build());
        when(groupMemberRepository.countByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(2L);
        when(groupChatRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(groupChat));
        when(groupGatheringRepository.save(any())).thenReturn(group);

        groupService.joinGroup(newUserId, GROUP_ID);

        assertEquals(GroupStatus.FULL, group.getStatus());
    }

    @Test
    void joinGroupShouldThrowForClosedGroup() {
        openGroup.setStatus(GroupStatus.CLOSED);
        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));

        assertThrows(IllegalStateException.class,
                () -> groupService.joinGroup(2L, GROUP_ID));
    }

    @Test
    void joinGroupShouldThrowWhenJoiningOwnGroup() {
        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));

        assertThrows(IllegalStateException.class,
                () -> groupService.joinGroup(USER_ID, GROUP_ID));
    }

    @Test
    void joinGroupShouldThrowWhenAlreadyInEventGroup() {
        Long newUserId = 2L;
        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, newUserId, GroupMemberStatus.ACTIVE))
                .thenReturn(false);
        when(groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(EVENT_ID, newUserId))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> groupService.joinGroup(newUserId, GROUP_ID));
    }

    // ========== leaveGroup tests ==========

    @Test
    void leaveGroupShouldSucceedForRegularMember() {
        Long memberId = 2L;
        GroupMember regularMember = GroupMember.builder()
                .id(2L).groupId(GROUP_ID).userId(memberId)
                .role(GroupMemberRole.MEMBER).status(GroupMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now()).build();

        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, memberId))
                .thenReturn(Optional.of(regularMember));
        when(groupMemberRepository.save(any())).thenReturn(regularMember);
        when(groupMemberRepository.countByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(1L);
        when(groupGatheringRepository.save(any())).thenReturn(openGroup);

        LeaveGroupResponse response = groupService.leaveGroup(memberId, GROUP_ID);

        assertNotNull(response);
        assertEquals(GROUP_ID, response.groupId());
        assertEquals(GroupMemberStatus.LEFT, regularMember.getStatus());
    }

    @Test
    void leaveGroupShouldCloseGroupWhenCreatorIsLastMember() {
        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(creatorMember));
        when(groupMemberRepository.save(any())).thenReturn(creatorMember);
        when(groupMemberRepository.findActiveByGroupIdOrderByJoinedAt(GROUP_ID))
                .thenReturn(Collections.emptyList());
        when(groupGatheringRepository.save(any())).thenReturn(openGroup);

        groupService.leaveGroup(USER_ID, GROUP_ID);

        assertEquals(GroupStatus.CLOSED, openGroup.getStatus());
    }

    @Test
    void leaveGroupShouldTransferCreatorRoleWhenOtherMembersExist() {
        Long newCreatorId = 2L;
        GroupMember newCreatorMember = GroupMember.builder()
                .id(2L).groupId(GROUP_ID).userId(newCreatorId)
                .role(GroupMemberRole.MEMBER).status(GroupMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now().minusMinutes(5)).build();

        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(creatorMember));
        when(groupMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.findActiveByGroupIdOrderByJoinedAt(GROUP_ID))
                .thenReturn(List.of(creatorMember, newCreatorMember));
        when(groupMemberRepository.countByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(1L);
        when(groupGatheringRepository.save(any())).thenReturn(openGroup);

        groupService.leaveGroup(USER_ID, GROUP_ID);

        assertEquals(GroupMemberRole.CREATOR, newCreatorMember.getRole());
        assertEquals(newCreatorId, openGroup.getCreatorId());
    }

    @Test
    void leaveGroupShouldThrowWhenNotMember() {
        when(groupGatheringRepository.findByIdWithLock(GROUP_ID)).thenReturn(Optional.of(openGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, 99L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> groupService.leaveGroup(99L, GROUP_ID));
    }
}
