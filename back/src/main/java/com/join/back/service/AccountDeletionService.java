package com.join.back.service;

import com.join.back.model.entity.Chat;
import com.join.back.model.entity.FriendGroup;
import com.join.back.model.entity.FriendGroupMember;
import com.join.back.model.entity.FriendGroupMemberStatus;
import com.join.back.model.entity.GroupChat;
import com.join.back.model.entity.GroupGathering;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deletes an account with everything tied to it — used by the admin panel and by the user
 * ("Удалить аккаунт" in the profile), which is how consent to personal data processing is withdrawn.
 * Outing confirmations and black-list entries go away by ON DELETE CASCADE.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
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
    private final FriendGroupRepository friendGroupRepository;
    private final FriendGroupMemberRepository friendGroupMemberRepository;
    private final FriendGroupChatMessageRepository friendGroupChatMessageRepository;
    private final FriendGroupService friendGroupService;

    @Transactional(transactionManager = "transactionManager")
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // 1. Delete group data for groups created by this user
        List<GroupGathering> createdGroups = groupGatheringRepository.findByCreatorId(userId);
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
        groupChatMessageRepository.deleteBySenderId(userId);
        groupMemberRepository.deleteByUserId(userId);

        // 3. Friend groups stay with the friends: leaving hands the creator role to the next member.
        // Groups nobody else is in are archived by leave() and removed below.
        for (FriendGroupMember membership : friendGroupMemberRepository
                .findByUserIdAndStatus(userId, FriendGroupMemberStatus.ACTIVE)) {
            friendGroupService.leave(userId, membership.getFriendGroupId());
        }
        List<FriendGroup> ownedFriendGroups = friendGroupRepository.findByCreatorId(userId);
        if (!ownedFriendGroups.isEmpty()) {
            List<Long> friendGroupIds = ownedFriendGroups.stream().map(FriendGroup::getId).toList();
            friendGroupChatMessageRepository.deleteByFriendGroupIdIn(friendGroupIds);
            friendGroupMemberRepository.deleteByFriendGroupIdIn(friendGroupIds);
            friendGroupRepository.deleteAll(ownedFriendGroups);
        }
        friendGroupChatMessageRepository.deleteBySenderId(userId);
        friendGroupMemberRepository.deleteByUserId(userId);

        // 4. Delete chats and related data (messages, icebreakers)
        List<Chat> userChats = chatRepository.findByUserId(userId);
        if (!userChats.isEmpty()) {
            List<Long> chatIds = userChats.stream().map(Chat::getId).toList();
            chatMessageRepository.deleteByChatIdIn(chatIds);
            chatIceBreakerRepository.deleteByChatIdIn(chatIds);
            chatRepository.deleteAll(userChats);
            // Chats reference matches; flush before the bulk match delete below.
            chatRepository.flush();
        }

        // 5. Delete matches
        matchRepository.deleteByUserId(userId);

        // 6. Delete event likes
        eventLikeRepository.deleteByUserId(userId);

        // 7. Delete notifications
        notificationRepository.deleteByUserId(userId);

        // 8. Delete support ticket and messages
        supportTicketRepository.findByUserId(userId).ifPresent(ticket -> {
            supportMessageRepository.deleteByTicketId(ticket.getId());
            supportTicketRepository.delete(ticket);
        });

        // 9. Delete user (user_interests cleaned up by JPA)
        userRepository.delete(user);
    }
}
