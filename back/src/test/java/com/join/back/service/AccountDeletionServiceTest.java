package com.join.back.service;

import com.join.back.model.entity.FriendGroup;
import com.join.back.model.entity.FriendGroupMember;
import com.join.back.model.entity.FriendGroupMemberStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatIceBreakerRepository;
import com.join.back.repository.ChatMessageRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.FriendGroupChatMessageRepository;
import com.join.back.repository.FriendGroupMemberRepository;
import com.join.back.repository.FriendGroupRepository;
import com.join.back.repository.GroupChatIceBreakerRepository;
import com.join.back.repository.GroupChatMessageRepository;
import com.join.back.repository.GroupChatRepository;
import com.join.back.repository.GroupGatheringRepository;
import com.join.back.repository.GroupMemberRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.NotificationRepository;
import com.join.back.repository.SupportMessageRepository;
import com.join.back.repository.SupportTicketRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private EventLikeRepository eventLikeRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatIceBreakerRepository chatIceBreakerRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private SupportTicketRepository supportTicketRepository;
    @Mock private GroupGatheringRepository groupGatheringRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupChatRepository groupChatRepository;
    @Mock private GroupChatMessageRepository groupChatMessageRepository;
    @Mock private GroupChatIceBreakerRepository groupChatIceBreakerRepository;
    @Mock private FriendGroupRepository friendGroupRepository;
    @Mock private FriendGroupMemberRepository friendGroupMemberRepository;
    @Mock private FriendGroupChatMessageRepository friendGroupChatMessageRepository;
    @Mock private FriendGroupService friendGroupService;

    @InjectMocks
    private AccountDeletionService accountDeletionService;

    @Test
    void leavesFriendGroupsAndRemovesOnesLeftEmpty() {
        User user = User.builder().id(USER_ID).build();
        FriendGroupMember membership = FriendGroupMember.builder()
                .friendGroupId(5L).userId(USER_ID).status(FriendGroupMemberStatus.ACTIVE).build();
        FriendGroup leftEmpty = FriendGroup.builder().id(7L).creatorId(USER_ID).archived(true).build();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(friendGroupMemberRepository.findByUserIdAndStatus(USER_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(friendGroupRepository.findByCreatorId(USER_ID)).thenReturn(List.of(leftEmpty));

        accountDeletionService.deleteAccount(USER_ID);

        verify(friendGroupService).leave(USER_ID, 5L);
        verify(friendGroupChatMessageRepository).deleteByFriendGroupIdIn(List.of(7L));
        verify(friendGroupMemberRepository).deleteByFriendGroupIdIn(List.of(7L));
        verify(friendGroupRepository).deleteAll(List.of(leftEmpty));
        verify(friendGroupMemberRepository).deleteByUserId(USER_ID);
        verify(matchRepository).deleteByUserId(USER_ID);
        verify(userRepository).delete(user);
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> accountDeletionService.deleteAccount(USER_ID));
        verify(userRepository, never()).delete(any());
    }
}
