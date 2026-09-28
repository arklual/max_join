package com.join.back.web.controller;

import com.join.back.service.MaxBotApiClient;
import com.join.back.model.entity.Event;
import com.join.back.service.MaxBotInfoService;
import com.join.back.service.PushkinPicksService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MaxWebhookController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = "max.webhook-secret=s3cret")
class MaxWebhookControllerTest {

    private static final String SECRET_HEADER = "X-Max-Bot-Api-Secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.join.back.service.MaxLinkService maxLinkService;

    @MockBean
    private com.join.back.service.MaxLoginService maxLoginService;

    @MockBean
    private MaxBotApiClient maxBotApiClient;

    @MockBean
    private MaxBotInfoService maxBotInfoService;

    @MockBean
    private PushkinPicksService pushkinPicksService;

    @MockBean
    private com.join.back.service.OutingFeedbackService outingFeedbackService;

    @MockBean
    private com.join.back.service.ContactRequestService contactRequestService;

    @MockBean
    private com.join.back.repository.UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(maxBotInfoService.getUsername()).thenReturn("join_bot");
        when(pushkinPicksService.appliesToMaxUser(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void botStartedWithInvitePayloadSendsOpenAppButtonWithPayload() throws Exception {
        String update = """
                {"update_type":"bot_started","timestamp":1,"chat_id":42,
                 "user":{"user_id":7,"first_name":"Ann"},"payload":"join_AbC123"}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        ArgumentCaptor<List<List<Map<String, Object>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(maxBotApiClient).sendMessageToChat(eq(42L), any(), isNull(), keyboard.capture());
        Map<String, Object> button = keyboard.getValue().get(0).get(0);
        assertThat(button).containsEntry("type", "open_app")
                .containsEntry("web_app", "join_bot")
                .containsEntry("payload", "join_abc123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wentButtonIsAnsweredInPlace() throws Exception {
        com.join.back.model.entity.User user = com.join.back.model.entity.User.builder().id(5L).maxId(7L).build();
        when(userRepository.findByMaxId(7L)).thenReturn(java.util.Optional.of(user));
        when(outingFeedbackService.handleButton(user, "went_42_yes")).thenReturn("🎉 Здорово!");
        String update = """
                {"update_type":"message_callback","timestamp":1,
                 "callback":{"timestamp":1,"callback_id":"cb-1","payload":"went_42_yes","user":{"user_id":7}}}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        ArgumentCaptor<List<List<Map<String, Object>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(maxBotApiClient).answerCallback(eq("cb-1"), eq("🎉 Здорово!"), keyboard.capture());
        assertThat(keyboard.getValue().get(0).get(0)).containsEntry("payload", "chat_42");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptButtonOpensTheNewChat() throws Exception {
        com.join.back.model.entity.User user = com.join.back.model.entity.User.builder().id(5L).maxId(7L).build();
        when(userRepository.findByMaxId(7L)).thenReturn(java.util.Optional.of(user));
        when(contactRequestService.handleButton(user, "accept_9"))
                .thenReturn(new com.join.back.service.ContactRequestService.ButtonReply("✅ Договорились общаться!", 55L));
        String update = """
                {"update_type":"message_callback","timestamp":1,
                 "callback":{"timestamp":1,"callback_id":"cb-2","payload":"accept_9","user":{"user_id":7}}}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        ArgumentCaptor<List<List<Map<String, Object>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(maxBotApiClient).answerCallback(eq("cb-2"), eq("✅ Договорились общаться!"), keyboard.capture());
        assertThat(keyboard.getValue().get(0).get(0)).containsEntry("payload", "chat_55");
    }

    @Test
    void startCommandMessageSendsWelcome() throws Exception {
        String update = """
                {"update_type":"message_created","timestamp":1,
                 "message":{"sender":{"user_id":7},"recipient":{"chat_id":99,"chat_type":"dialog"},
                            "body":{"mid":"m1","text":"/start"}}}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        verify(maxBotApiClient).sendMessageToChat(eq(99L), any(), isNull(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void welcomeHidesPushkinCardForUsersOutsideItsAgeRange() throws Exception {
        when(pushkinPicksService.appliesToMaxUser(7L)).thenReturn(false);
        String update = """
                {"update_type":"message_created","timestamp":1,
                 "message":{"sender":{"user_id":7},"recipient":{"chat_id":99,"chat_type":"dialog"},
                            "body":{"mid":"m1","text":"/start"}}}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<List<Map<String, Object>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(maxBotApiClient).sendMessageToChat(eq(99L), text.capture(), isNull(), keyboard.capture());
        assertThat(text.getValue()).doesNotContain("Пушкинск");
        assertThat(keyboard.getValue()).hasSize(1);
    }

    @Test
    void pushkinCommandExplainsAgeLimitInsteadOfListing() throws Exception {
        when(pushkinPicksService.appliesToMaxUser(7L)).thenReturn(false);
        String update = """
                {"update_type":"message_created","timestamp":1,
                 "message":{"sender":{"user_id":7},"recipient":{"chat_id":99,"chat_type":"dialog"},
                            "body":{"mid":"m1","text":"/pushkin"}}}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        verify(maxBotApiClient).sendMessageToChat(eq(99L), eq(MaxWebhookController.PUSHKIN_NOT_ELIGIBLE), isNull(), any());
        org.mockito.Mockito.verify(pushkinPicksService, org.mockito.Mockito.never()).forMaxUser(7L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pushkinCommandListsEventsWithDeepLinkButtons() throws Exception {
        Event event = Event.builder().id(42L).title("Щелкунчик").city("Москва")
                .eventDate(LocalDate.of(2026, 12, 25)).eventTime(LocalTime.of(19, 0))
                .price(new java.math.BigDecimal("1500")).build();
        when(pushkinPicksService.forMaxUser(7L)).thenReturn(new PushkinPicksService.Picks("Москва", List.of(event)));
        String update = """
                {"update_type":"message_created","timestamp":1,
                 "message":{"sender":{"user_id":7},"recipient":{"chat_id":99,"chat_type":"dialog"},
                            "body":{"mid":"m1","text":"/pushkin"}}}""";

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<List<Map<String, Object>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(maxBotApiClient).sendMessageToChat(eq(99L), text.capture(), eq("html"), keyboard.capture());
        assertThat(text.getValue()).contains("Щелкунчик").contains("25 декабря, 19:00").contains("Москва")
                .contains("от 1\u00A0500 ₽");
        assertThat(keyboard.getValue().get(0).get(0)).containsEntry("payload", "event_42");
        assertThat(keyboard.getValue().get(1).get(0)).containsEntry("payload", "pushkin");
    }

    @Test
    void botStartedWithLinkPayloadLinksTheMaxAccount() throws Exception {
        when(maxLinkService.link("tok123", 7L)).thenReturn(
                new com.join.back.service.MaxLinkService.Outcome(com.join.back.service.MaxLinkService.Result.LINKED, null));

        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_type\":\"bot_started\",\"chat_id\":42,\"user\":{\"user_id\":7},\"payload\":\"link_tok123\"}"))
                .andExpect(status().isOk());

        verify(maxLinkService).link("tok123", 7L);
        verify(maxBotApiClient).sendMessageToChat(eq(42L), any(), isNull(), any());
    }

    @Test
    void rejectsUpdateWithWrongSecret() throws Exception {
        mockMvc.perform(post("/api/max/webhook")
                        .header(SECRET_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_type\":\"bot_started\",\"chat_id\":1}"))
                .andExpect(status().isUnauthorized());

        verify(maxBotApiClient, never()).sendMessageToChat(anyLong(), any(), any(), any());
    }
}
