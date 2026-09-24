package com.join.back.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.User;
import com.join.back.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates STOMP sessions. Browsers can't set custom headers on the
 * WebSocket handshake, so the client sends its credentials in the CONNECT
 * frame instead ({@code X-Max-Init-Data} from the mini app, or
 * {@code Authorization: Bearer <jwt>} from the mobile app). The session
 * principal's name is the JOIN user id, which is what
 * {@code convertAndSendToUser(userId, ...)} targets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final WebAppInitDataValidator initDataValidator;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Value("${max.bot-token}")
    private String botToken;

    @Value("${telegram.bot-token:}")
    private String telegramBotToken;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Long userId = resolveUserId(accessor);
            if (userId != null) {
                String name = userId.toString();
                accessor.setUser((Principal) () -> name);
            } else {
                log.debug("STOMP CONNECT without valid credentials — anonymous session");
            }
        }
        return message;
    }

    private Long resolveUserId(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (auth != null && auth.startsWith(BEARER)) {
            try {
                return jwtService.verify(auth.substring(BEARER.length()).trim());
            } catch (Exception e) {
                log.warn("STOMP auth: invalid JWT — {}", e.getClass().getSimpleName());
            }
        }

        Long userId = fromInitData(accessor.getFirstNativeHeader(MaxAuthFilter.HEADER), botToken, Messenger.MAX);
        if (userId == null) {
            userId = fromInitData(accessor.getFirstNativeHeader(TelegramAuthFilter.HEADER), telegramBotToken, Messenger.TELEGRAM);
        }
        return userId;
    }

    private Long fromInitData(String initData, String token, Messenger messenger) {
        if (initData == null || initData.isEmpty() || token == null || token.isBlank()
                || !initDataValidator.validate(initData, token)) {
            return null;
        }
        try {
            String userJson = initDataValidator.parseInitData(initData).get("user");
            if (userJson == null) return null;
            long externalId = objectMapper.readTree(userJson).get("id").asLong();
            return userService.findByMessengerId(messenger, externalId).map(User::getId).orElse(null);
        } catch (Exception e) {
            log.warn("STOMP auth: failed to parse {} init data — {}", messenger, e.getMessage());
        }
        return null;
    }
}
