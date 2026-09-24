package com.join.back.service;

import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaxLinkServiceTest {

    private UserRepository userRepository;
    private MaxLinkService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        MaxBotInfoService botInfo = mock(MaxBotInfoService.class);
        when(botInfo.getUsername()).thenReturn("join_bot");
        service = new MaxLinkService(userRepository, botInfo);
        user = User.builder().id(7L).email("a@b.c").firstName("Аня").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.findByMaxId(any())).thenReturn(Optional.empty());
    }

    private String token() {
        String url = service.createLinkUrl(7L);
        assertTrue(url.startsWith("https://max.ru/join_bot?start=link_"));
        return url.substring(url.indexOf("link_") + "link_".length());
    }

    @Test
    void linksMaxAccountOnceAndConsumesToken() {
        String token = token();

        assertEquals(MaxLinkService.Result.LINKED, service.link(token, 555L).result());
        assertEquals(555L, user.getMaxId());
        verify(userRepository).save(user);

        // one-time: the same token can't be reused
        assertEquals(MaxLinkService.Result.EXPIRED, service.link(token, 555L).result());
    }

    @Test
    void refusesMaxAccountThatBelongsToAnotherUser() {
        User other = User.builder().id(9L).maxId(555L).build();
        when(userRepository.findByMaxId(555L)).thenReturn(Optional.of(other));

        assertEquals(MaxLinkService.Result.TAKEN_BY_ANOTHER, service.link(token(), 555L).result());
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownTokenIsExpired() {
        assertEquals(MaxLinkService.Result.EXPIRED, service.link("nope", 555L).result());
    }
}
