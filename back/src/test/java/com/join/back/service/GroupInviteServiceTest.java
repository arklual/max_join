package com.join.back.service;

import com.join.back.model.dto.GroupInviteCandidateResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupInviteServiceTest {

    @Mock private GroupGatheringRepository groupGatheringRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private FriendGroupMemberRepository friendGroupMemberRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private MessengerNotificationService messengerNotificationService;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private GroupInviteService service;

    private final User me = User.builder().id(1L).firstName("Аня").build();
    private final User friend = User.builder().id(2L).firstName("Борис").maxId(200L).build();
    private GroupGathering group;

    @BeforeEach
    void setUp() {
        group = GroupGathering.builder().id(10L).eventId(5L).creatorId(1L).maxSize(4).status(GroupStatus.OPEN).build();
        when(groupGatheringRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(10L, 1L, GroupMemberStatus.ACTIVE)).thenReturn(true);
        when(chatRepository.findByUserId(1L)).thenReturn(List.of(
                Chat.builder().id(7L).user1Id(1L).user2Id(2L).eventId(3L).build()));
        when(friendGroupMemberRepository.findByUserIdAndStatus(eq(1L), eq(FriendGroupMemberStatus.ACTIVE), any()))
                .thenReturn(Page.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(userRepository.findAllById(any())).thenReturn(List.of(friend));
        when(eventRepository.findById(5L)).thenReturn(Optional.of(
                Event.builder().id(5L).title("Щелкунчик").eventDate(LocalDate.of(2026, 12, 25)).build()));
    }

    @Test
    void listsMatchCompanionsAsCandidates() {
        List<GroupInviteCandidateResponse> candidates = service.getCandidates(1L, 10L);

        assertEquals(1, candidates.size());
        assertEquals(2L, candidates.get(0).userId());
        assertEquals("AVAILABLE", candidates.get(0).status());
    }

    @Test
    void invitesContactAndNotifiesInAppAndMessenger() {
        GroupInviteCandidateResponse result = service.invite(1L, 10L, 2L);

        assertEquals("INVITED", result.status());
        verify(notificationService).createGroupInviteNotification(2L, 10L, "Аня", "Щелкунчик");
        verify(messengerNotificationService).sendGroupInvite(friend, "Аня", "Щелкунчик", "25 декабря", 10L);
    }

    @Test
    void doesNotInviteTwice() {
        when(notificationRepository.existsByUserIdAndGroupIdAndType(2L, 10L, NotificationType.GROUP_INVITE)).thenReturn(true);

        assertThrows(UserActionException.class, () -> service.invite(1L, 10L, 2L));
        verify(notificationService, never()).createGroupInviteNotification(anyLong(), anyLong(), any(), any());
    }

    @Test
    void rejectsStrangers() {
        assertThrows(UserActionException.class, () -> service.invite(1L, 10L, 99L));
    }

    @Test
    void rejectsFriendAlreadyGoingWithAnotherGroup() {
        when(groupGatheringRepository.existsActiveGroupMemberByEventIdAndUserId(5L, 2L)).thenReturn(true);

        assertThrows(UserActionException.class, () -> service.invite(1L, 10L, 2L));
    }

    @Test
    void onlyMembersCanInvite() {
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(10L, 1L, GroupMemberStatus.ACTIVE)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getCandidates(1L, 10L));
    }
}
