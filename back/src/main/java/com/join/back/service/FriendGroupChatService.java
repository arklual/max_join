package com.join.back.service;

import com.join.back.model.dto.FriendGroupChatMessageResponse;
import com.join.back.model.entity.FriendGroupChatMessage;
import com.join.back.model.entity.FriendGroupMember;
import com.join.back.model.entity.FriendGroupMemberStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.FriendGroupChatMessageRepository;
import com.join.back.repository.FriendGroupMemberRepository;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendGroupChatService {

    private final FriendGroupChatMessageRepository messageRepository;
    private final FriendGroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(transactionManager = "transactionManager")
    public FriendGroupChatMessageResponse send(Long friendGroupId, Long senderId, String text) {
        assertActiveMember(friendGroupId, senderId);

        LocalDateTime now = LocalDateTime.now();
        FriendGroupChatMessage message = FriendGroupChatMessage.builder()
                .friendGroupId(friendGroupId)
                .senderId(senderId)
                .text(text)
                .createdAt(now)
                .build();
        message = messageRepository.save(message);

        User sender = userRepository.findById(senderId).orElse(null);

        FriendGroupChatMessageResponse response = new FriendGroupChatMessageResponse(
                message.getId(),
                senderId,
                sender != null ? sender.getFirstName() : null,
                sender != null ? sender.getPhoto() : null,
                message.getText(),
                message.getCreatedAt()
        );

        messagingTemplate.convertAndSend("/topic/friend-group/" + friendGroupId, response);
        return response;
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<FriendGroupChatMessageResponse> getMessages(Long friendGroupId, Long currentUserId, Pageable pageable) {
        assertActiveMember(friendGroupId, currentUserId);

        List<Long> activeMemberIds = memberRepository
                .findByFriendGroupIdAndStatusOrderByJoinedAtAsc(friendGroupId, FriendGroupMemberStatus.ACTIVE)
                .stream()
                .map(FriendGroupMember::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> usersMap = userRepository.findAllById(activeMemberIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return messageRepository.findByFriendGroupIdOrderByCreatedAtAsc(friendGroupId, pageable)
                .map(msg -> {
                    User s = usersMap.get(msg.getSenderId());
                    return new FriendGroupChatMessageResponse(
                            msg.getId(),
                            msg.getSenderId(),
                            s != null ? s.getFirstName() : null,
                            s != null ? s.getPhoto() : null,
                            msg.getText(),
                            msg.getCreatedAt()
                    );
                });
    }

    private void assertActiveMember(Long friendGroupId, Long userId) {
        boolean isMember = memberRepository
                .existsByFriendGroupIdAndUserIdAndStatus(friendGroupId, userId, FriendGroupMemberStatus.ACTIVE);
        if (!isMember) {
            throw new AccessDeniedException("User is not an active member of this friend group");
        }
    }
}
