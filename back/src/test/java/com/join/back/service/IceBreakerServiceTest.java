package com.join.back.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.config.IceBreakerProperties;
import com.join.back.model.dto.IceBreakerResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.ChatIceBreaker;
import com.join.back.model.entity.User;
import com.join.back.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IceBreakerServiceTest {

    private ChatIceBreakerRepository iceBreakerRepository;
    private IceBreakerService service;

    @BeforeEach
    void setUp() {
        ChatRepository chatRepository = mock(ChatRepository.class);
        iceBreakerRepository = mock(ChatIceBreakerRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        IceBreakerProperties properties = new IceBreakerProperties();
        properties.setEnabled(false); // template suggestions — deterministic, no AI call

        Chat chat = new Chat();
        chat.setId(1L);
        chat.setUser1Id(10L);
        chat.setUser2Id(20L);
        chat.setEventId(99L);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(10L)).thenReturn(Optional.of(User.builder().id(10L).firstName("Анна").interests(List.of()).build()));
        when(userRepository.findById(20L)).thenReturn(Optional.of(User.builder().id(20L).firstName("Борис").interests(List.of()).build()));
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());
        when(iceBreakerRepository.findByChatIdAndUserId(any(), any())).thenReturn(Optional.empty());

        service = new IceBreakerService(chatRepository, iceBreakerRepository, mock(GroupChatRepository.class),
                mock(GroupChatIceBreakerRepository.class), mock(GroupMemberRepository.class),
                userRepository, eventRepository, properties, new ObjectMapper());
    }

    @Test
    void eachParticipantGetsSuggestionsAddressingTheCompanion() {
        IceBreakerResponse forAnna = service.getIceBreakers(1L, 10L);
        IceBreakerResponse forBoris = service.getIceBreakers(1L, 20L);

        assertThat(String.join(" ", forAnna.suggestions())).contains("Борис").doesNotContain("Анна");
        assertThat(String.join(" ", forBoris.suggestions())).contains("Анна").doesNotContain("Борис");

        verify(iceBreakerRepository).findByChatIdAndUserId(1L, 10L);
        verify(iceBreakerRepository).findByChatIdAndUserId(1L, 20L);
        ArgumentCaptor<ChatIceBreaker> saved = ArgumentCaptor.forClass(ChatIceBreaker.class);
        verify(iceBreakerRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ChatIceBreaker::getUserId).containsExactly(10L, 20L);
    }
}
