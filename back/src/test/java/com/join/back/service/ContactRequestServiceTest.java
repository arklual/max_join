package com.join.back.service;

import com.join.back.model.dto.MatchResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.MatchStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactRequestServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private ChatService chatService;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserBlockService userBlockService;
    @Mock private MessengerNotificationService messengerNotificationService;

    @InjectMocks
    private ContactRequestService service;

    private final User anya = User.builder().id(1L).firstName("Аня").build();
    private final User boris = User.builder().id(2L).firstName("Борис").build();
    private final Event event = Event.builder().id(5L).title("Щелкунчик").eventDate(LocalDate.now().plusDays(5)).build();
    private Match match;

    @BeforeEach
    void setUp() {
        match = Match.builder().id(9L).user1Id(1L).user2Id(2L).eventId(5L).status(MatchStatus.NEW).build();
        when(matchRepository.findById(9L)).thenReturn(Optional.of(match));
        when(matchRepository.findByUserId(anyLong())).thenReturn(List.of(match));
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(userRepository.findById(1L)).thenReturn(Optional.of(anya));
        when(userRepository.findById(2L)).thenReturn(Optional.of(boris));
        when(userRepository.findAllById(any())).thenReturn(List.of(anya, boris));
        when(chatRepository.findByMatchId(9L)).thenReturn(Optional.empty());
        when(chatService.createChat(9L, 1L, 2L, 5L)).thenReturn(Chat.builder().id(77L).build());
    }

    @Test
    void chatOpensOnlyAfterTheInvitationIsAccepted() {
        MatchResponse requested = service.request(9L, 1L);
        assertEquals("REQUESTED", requested.status());
        assertTrue(requested.requestedByMe());
        verify(messengerNotificationService).sendContactRequest(boris, "Аня", "Щелкунчик", 9L);
        verify(chatService, never()).createChat(anyLong(), anyLong(), anyLong(), anyLong());

        MatchResponse accepted = service.accept(9L, 2L);
        assertEquals("ACCEPTED", accepted.status());
        verify(chatService).createChat(9L, 1L, 2L, 5L);
        verify(messengerNotificationService).sendContactAccepted(anya, "Борис", "Щелкунчик", 77L);
    }

    @Test
    void inviterCannotAcceptOwnInvitation() {
        service.request(9L, 1L);
        assertThrows(UserActionException.class, () -> service.accept(9L, 1L));
    }

    @Test
    void mutualInvitationsAccept() {
        service.request(9L, 1L);
        assertEquals("ACCEPTED", service.request(9L, 2L).status());
        verify(chatService).createChat(9L, 1L, 2L, 5L);
    }

    @Test
    void declineHidesTheSuggestionWithoutNotifyingTheInviter() {
        service.request(9L, 1L);
        assertEquals("DECLINED", service.decline(9L, 2L).status());
        assertTrue(service.getPending(1L).isEmpty());
        verify(messengerNotificationService, never()).sendContactAccepted(any(), any(), any(), anyLong());
    }

    @Test
    void requestsToMeComeFirstInTheList() {
        service.request(9L, 2L);
        List<MatchResponse> pending = service.getPending(1L);
        assertEquals(1, pending.size());
        assertEquals("Борис", pending.get(0).companionName());
        assertEquals(false, pending.get(0).requestedByMe());
    }

    @Test
    void blockedPairCannotInvite() {
        when(userBlockService.hasBlocked(2L, 1L)).thenReturn(true);
        assertThrows(UserActionException.class, () -> service.request(9L, 1L));
    }

    @Test
    void botButtonsWork() {
        assertTrue(service.handleButton(anya, "invite_9").text().startsWith("🤝"));
        ContactRequestService.ButtonReply reply = service.handleButton(boris, "accept_9");
        assertEquals(77L, reply.chatId());
        assertEquals(null, service.handleButton(anya, "went_1_yes"));
    }
}
