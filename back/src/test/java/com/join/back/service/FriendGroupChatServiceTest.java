package com.join.back.service;

import com.join.back.model.dto.FriendGroupChatMessageResponse;
import com.join.back.model.entity.FriendGroupChatMessage;
import com.join.back.model.entity.FriendGroupMemberStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.FriendGroupChatMessageRepository;
import com.join.back.repository.FriendGroupMemberRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendGroupChatServiceTest {

    private static final Long GROUP_ID = 100L;
    private static final Long SENDER_ID = 1L;
    private static final Long OUTSIDER_ID = 99L;

    @Mock
    private FriendGroupChatMessageRepository messageRepository;
    @Mock
    private FriendGroupMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private FriendGroupChatService service;

    @Test
    void send_happyPath_persistsAndBroadcasts() {
        when(memberRepository.existsByFriendGroupIdAndUserIdAndStatus(GROUP_ID, SENDER_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(true);
        when(messageRepository.save(any(FriendGroupChatMessage.class))).thenAnswer(inv -> {
            FriendGroupChatMessage msg = inv.getArgument(0);
            msg.setId(1000L);
            return msg;
        });
        User sender = new User();
        sender.setId(SENDER_ID);
        sender.setFirstName("Alex");
        when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));

        FriendGroupChatMessageResponse response = service.send(GROUP_ID, SENDER_ID, "hi");

        assertNotNull(response);
        assertEquals("hi", response.text());
        assertEquals("Alex", response.senderName());
        verify(messageRepository).save(any(FriendGroupChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/friend-group/" + GROUP_ID), any(Object.class));
    }

    @Test
    void send_notActiveMember_throwsAccessDenied() {
        when(memberRepository.existsByFriendGroupIdAndUserIdAndStatus(GROUP_ID, OUTSIDER_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.send(GROUP_ID, OUTSIDER_ID, "hi"));

        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void getMessages_notActiveMember_throwsAccessDenied() {
        when(memberRepository.existsByFriendGroupIdAndUserIdAndStatus(GROUP_ID, OUTSIDER_ID, FriendGroupMemberStatus.ACTIVE))
                .thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getMessages(GROUP_ID, OUTSIDER_ID,
                        org.springframework.data.domain.PageRequest.of(0, 20)));
    }
}
